package com.iexec.worker.docker;

import com.iexec.common.result.ResultModel;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Volume;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class DockerService {

    private final String REMOTE_PATH = "/iexec";
    private final String LOCAL_PATH = "/home/james/iexec2";
    private final String LOCAL_BASE_VOLUME = "iexec-worker-volume";

    private DefaultDockerClient docker;
    private Map<String, MetadataResult> metadataResultMap = new HashMap<>();

    @PostConstruct
    public void onPostConstruct() throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
    }

    public MetadataResult dockerRun(String taskId, String image, String cmd) {
        //TODO: check image equals image:tag
        final Volume volume = createVolume(LOCAL_BASE_VOLUME + "-" + taskId);
        final HostConfig hostConfig = createHostConfig(LOCAL_BASE_VOLUME + "-" + taskId, REMOTE_PATH);
        final ContainerConfig containerConfig = createContainerConfig(image, cmd, hostConfig);

        MetadataResult metadataResult = null;
        try {
            pullImage(image);
            String id = startContainer(containerConfig);
            waitContainerForExitStatus(id);

            metadataResult = MetadataResult.builder()
                    .image(image)
                    .cmd(cmd)
                    .containerId(id)
                    .stdout(getDockerLogs(id))
                    .build();

            metadataResultMap.put(taskId, metadataResult);//save metadataResult (without zip payload) in memory
            copyFolderFromContainerToHost(id, REMOTE_PATH, LOCAL_PATH + "/" + taskId);
            zipTaskResult(taskId);
            docker.removeContainer(id);
        } catch (DockerException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return metadataResult;
    }

    private Volume createVolume(String volumeName) {
        final Volume toCreate = Volume.builder()
                .name(volumeName)
                .driver("local")
                .build();
        try {
            //docker.removeVolume(volumeName);
            return docker.createVolume(toCreate);
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
        return toCreate;
    }

    private ContainerConfig createContainerConfig(String imageWithTag, String cmd, HostConfig hostConfig) {
        ContainerConfig.Builder builder = ContainerConfig.builder()
                .image(imageWithTag)
                .hostConfig(hostConfig);
        ContainerConfig containerConfig;
        if (cmd == null || cmd.isEmpty()) {
            containerConfig = builder.build();
        } else {
            containerConfig = builder.cmd(cmd).build();
        }
        return containerConfig;
    }

    private HostConfig createHostConfig(String from, String to) {
        return HostConfig.builder()
                .appendBinds(HostConfig.Bind.from(from)
                        .to(to)
                        .readOnly(false)
                        .build())
                .build();
    }

    private void pullImage(String image) throws DockerException, InterruptedException {
        docker.pull(image);
    }

    private String startContainer(ContainerConfig containerConfig) throws DockerException, InterruptedException {
        ContainerCreation creation = docker.createContainer(containerConfig);
        String id = creation.id();
        docker.startContainer(id);
        return id;
    }

    private void waitContainerForExitStatus(String id) throws DockerException, InterruptedException {
        //TODO: add category timeout
        while (!docker.inspectContainer(id).state().status().equals("exited")) {
            Thread.sleep(1000);
            log.info("Container just exited [containerId={}, status={}]", id, docker.inspectContainer(id).state().status());
        }
    }

    private String getDockerLogs(String id) throws DockerException, InterruptedException {
        return docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).readFully();
    }

    private void copyFolderFromContainerToHost(String id, String remotePath, String localPath) throws DockerException, InterruptedException, IOException {
        final TarArchiveInputStream tarStream = new TarArchiveInputStream(docker.archiveContainer(id, remotePath));

        TarArchiveEntry entry;
        while ((entry = tarStream.getNextTarEntry()) != null) {
            log.info(entry.getName());
            if (entry.isDirectory()) {
                continue;
            }
            File curfile = new File(localPath, entry.getName());
            File parent = curfile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            IOUtils.copy(tarStream, new FileOutputStream(curfile));
        }
    }

    private void zipTaskResult(String taskId) {
        String folderToZip = LOCAL_PATH + "/" + taskId;
        String zipName = folderToZip + ".zip";
        try {
            zipFolder(Paths.get(folderToZip), Paths.get(zipName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
        Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zos.close();
    }

    public ResultModel getResultModelWithPayload(String taskId) {
        MetadataResult metadataResult = metadataResultMap.get(taskId);
        byte[] zipResultAsBytes = getZipResultAsBytes(taskId);
        return ResultModel.builder()
                .taskId(taskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .stdout(metadataResult.getStdout())
                .zip(zipResultAsBytes).build();
    }

    private byte[] getZipResultAsBytes(String taskId) {
        byte[] resultByte = null;
        try {
            File resultZip = new File(LOCAL_PATH + "/" + taskId + ".zip");
            resultByte = FileUtils.readFileToByteArray(resultZip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resultByte;
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

}
