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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final String STDOUT_FILENAME = "stdout.txt";
    private final String DOCKER_BASE_VOLUME_NAME = "iexec-worker-";

    private DefaultDockerClient docker;
    private Map<String, MetadataResult> metadataResultMap = new HashMap<>();
    private WorkerConfigurationService configurationService;

    public DockerService(WorkerConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    private static boolean createFileWithContent(String directoryPath, String filename, String data) {
        if (createDirectories(directoryPath)) {
            Path path = Paths.get(directoryPath + "/" + filename);
            byte[] strToBytes = data.getBytes();
            try {
                Files.write(path, strToBytes);
                log.debug("File created [directoryPath:{}, filename:{}]", directoryPath, filename);
                return true;
            } catch (IOException e) {
                log.error("Failed to create file [directoryPath:{}, filename:{}]", directoryPath, filename);
            }
        } else {
            log.error("Failed to create base directory [directoryPath:{}]", directoryPath);
        }
        return false;
    }

    private static boolean createDirectories(String directoryPath) {
        File baseDirectory = new File(directoryPath);
        if (!baseDirectory.exists()) {
            return baseDirectory.mkdirs();
        } else {
            return true;
        }
    }

    private static HostConfig createHostConfig(Volume from, String to) {
        return HostConfig.builder()
                .appendBinds(HostConfig.Bind.from(from)
                        .to(to)
                        .readOnly(false)
                        .build())
                .build();
    }

    private static ContainerConfig createContainerConfig(String imageWithTag, String cmd, HostConfig hostConfig) {
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

    private static void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
        Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug(file.toAbsolutePath().toString());
                zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zos.close();
    }

    @PostConstruct
    public void onPostConstruct() throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

    public MetadataResult dockerRun(String taskId, String image, String cmd) {
        //TODO: check image equals image:tag
        MetadataResult metadataResult = MetadataResult.builder()
                .image(image)
                .cmd(cmd)
                .build();

        boolean isImagePulled = pullImage(taskId, image);

        if (isImagePulled) {
            final Volume volume = createVolume(taskId);
            final HostConfig hostConfig = createHostConfig(volume, REMOTE_PATH);
            final ContainerConfig containerConfig = createContainerConfig(image, cmd, hostConfig);

            String containerId = startContainer(taskId, containerConfig);
            if (!containerId.isEmpty()) {
                metadataResult.setContainerId(containerId);
                boolean executionDone = waitContainerForExitStatus(taskId, containerId);

                if (executionDone) {
                    String dockerLogs = getDockerLogs(metadataResult.getContainerId());
                    createStdoutFile(taskId, dockerLogs);
                } else {
                    createStdoutFile(taskId, "Computation failed");
                }

                InputStream containerResult = getContainerResultArchive(containerId);
                copyResultToDisk(containerResult, taskId);

                removeContainer(containerId);
                removeVolume(taskId);
            } else {
                createStdoutFile(taskId, "Failed to start container");
            }
        } else {
            createStdoutFile(taskId, "Failed to pull image");
        }

        zipTaskResult(configurationService.getResultBaseDir(), taskId);

        metadataResultMap.put(taskId, metadataResult);//save metadataResult (without zip payload) in memory
        return metadataResult;
    }

    private boolean pullImage(String taskId, String image) {
        try {
            log.info("Image pull started [taskId:{}, image:{}]",
                    taskId, image);
            docker.pull(image);
            log.info("Image pull completed [taskId:{}, image:{}]",
                    taskId, image);
        } catch (DockerException | InterruptedException e) {
            log.error("Image pull failed [taskId:{}, image:{}]", taskId, image);
            return false;
        }
        return true;
    }

    private Volume createVolume(String taskId) {
        String volumeName = getTaskVolumeName(taskId);
        Volume toCreate;
        toCreate = Volume.builder()
                .name(volumeName)
                .driver("local")
                .build();
        try {
            return docker.createVolume(toCreate);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to create volume [taskId:{}, volumeName:{}]", taskId, volumeName);
        }
        return toCreate;
    }

    private String startContainer(String taskId, ContainerConfig containerConfig) {
        String id = "";
        try {
            ContainerCreation creation = docker.createContainer(containerConfig);
            id = creation.id();
            if (id != null) {
                docker.startContainer(id);
                log.info("Computation started [taskId:{}, image:{}, cmd:{}]",
                        taskId, containerConfig.image(), containerConfig.cmd());
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Computation failed to start[taskId:{}, image:{}, cmd:{}]",
                    taskId, containerConfig.image(), containerConfig.cmd());
            removeContainer(id);
            id = "";
        }
        return id;
    }

    private boolean waitContainerForExitStatus(String taskId, String containerId) {
        //TODO: add category timeout
        boolean isExecutionDone = false;
        try {
            while (!docker.inspectContainer(containerId).state().status().equals("exited")) {
                Thread.sleep(1000);
                log.info("Computation running [taskId:{}, containerId:{}, status:{}]",
                        taskId, containerId, docker.inspectContainer(containerId).state().status());
            }
            log.info("Computation completed [taskId:{}, containerId:{}]",
                    taskId, containerId);
            isExecutionDone = true;
        } catch (DockerException | InterruptedException e) {
            log.error("Computation failed [taskId:{}, containerId:{}]",
                    taskId, containerId);
        }
        return isExecutionDone;
    }

    private String getDockerLogs(String id) {
        String logs = "";
        if (!id.isEmpty()) {
            try {
                logs = docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).readFully();
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to get logs of computation [containerId:{}]", id);
                logs = "Failed to get logs of computation";
            }
        }
        return logs;
    }

    private boolean createStdoutFile(String taskId, String stdoutContent) {
        log.info("Stdout file added to result folder [taskId:{}]", taskId);
        return createFileWithContent(configurationService.getResultBaseDir() + "/" + taskId, STDOUT_FILENAME, stdoutContent);
    }

    private InputStream getContainerResultArchive(String containerId) {
        InputStream containerResultArchive = null;
        try {
            containerResultArchive = docker.archiveContainer(containerId, REMOTE_PATH);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container archive [containerId:{}]", containerId);
        }
        return containerResultArchive;
    }

    private void copyResultToDisk(InputStream containerResultArchive, String taskId) {
        try {
            final TarArchiveInputStream tarStream = new TarArchiveInputStream(containerResultArchive);

            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                log.debug(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(configurationService.getResultBaseDir() + "/" + taskId, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                IOUtils.copy(tarStream, new FileOutputStream(curfile));
            }
            log.info("Results from remote added to result folder [taskId:{}]", taskId);
        } catch (IOException e) {
            log.error("Failed to copy container results to disk [taskId:{}]", taskId);
        }
    }

    private void zipTaskResult(String localPath, String taskId) {
        String folderToZip = localPath + "/" + taskId;
        String zipName = folderToZip + ".zip";
        try {
            zipFolder(Paths.get(folderToZip), Paths.get(zipName));
            log.info("Result folder zip completed [taskId:{}]", taskId);
        } catch (Exception e) {
            log.error("Failed to zip task result [taskId:{}]", taskId);
        }
    }

    public ResultModel getResultModelWithZip(String taskId) {
        MetadataResult metadataResult = metadataResultMap.get(taskId);
        byte[] zipResultAsBytes = new byte[0];
        String zipLocation = configurationService.getResultBaseDir() + "/" + taskId + ".zip";
        try {
            zipResultAsBytes = Files.readAllBytes(Paths.get(zipLocation));
        } catch (IOException e) {
            log.error("Failed to get zip result [taskId:{}, zipLocation:{}]", taskId, zipLocation);
        }

        return ResultModel.builder()
                .taskId(taskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .zip(zipResultAsBytes).build();
    }

    private void removeContainer(String containerId) {
        if (!containerId.isEmpty()) {
            try {
                docker.removeContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to remove container [containerId:{}]", containerId);
            }
        }
    }

    private void removeVolume(String taskId) {
        String volumeName = getTaskVolumeName(taskId);
        if (taskId != null) {
            try {
                docker.removeVolume(volumeName);
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to remove volume [taskId:{}, volumeName:{}]", taskId, volumeName);
            }
        }
    }

    private String getTaskVolumeName(String taskId) {
        return DOCKER_BASE_VOLUME_NAME + taskId;
    }

}
