package com.iexec.worker.docker;

import com.iexec.common.result.ResultModel;
import com.iexec.worker.utils.WorkerConfigurationService;
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
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
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

    private DefaultDockerClient docker;
    private Map<String, MetadataResult> metadataResultMap = new HashMap<>();
    private WorkerConfigurationService workerConfigurationService;

    public DockerService(WorkerConfigurationService workerConfigurationService) {
        this.workerConfigurationService = workerConfigurationService;
    }

    @PostConstruct
    public void onPostConstruct() throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
    }

    public MetadataResult dockerRun(String taskId, String image, String cmd) {
        //TODO: check image equals image:tag
        final Volume volume = createVolume(workerConfigurationService.getWorkerVolumeName() + "-" + taskId);
        final HostConfig hostConfig = createHostConfig(workerConfigurationService.getWorkerVolumeName() + "-" + taskId, REMOTE_PATH);
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
                    .volumeName(volume.name())
                    .volumeMountPoint(volume.mountpoint())
                    .build();

            metadataResultMap.put(taskId, metadataResult);//save metadataResult (without zip payload) in memory

            docker.removeContainer(id);
        } catch (DockerException | InterruptedException e) {
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

    public ResultModel getResultModelWithPayload(String taskId) {
        MetadataResult metadataResult = metadataResultMap.get(taskId);

        byte[] zipResultAsBytes = getZipResultAsBytes(metadataResult.getVolumeMountPoint());

        try {
            docker.removeVolume(metadataResult.getVolumeName());
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }

        return ResultModel.builder()
                .taskId(taskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .stdout(metadataResult.getStdout())
                .zip(zipResultAsBytes).build();
    }

    private byte[] getZipResultAsBytes(String pathToZip) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ZipOutputStream zos = new ZipOutputStream(baos);
            Path sourceFolderPath = Paths.get(pathToZip);
            Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
            zos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

}
