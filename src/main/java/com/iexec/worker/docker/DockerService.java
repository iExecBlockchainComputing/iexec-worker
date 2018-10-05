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
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        MetadataResult metadataResult = MetadataResult.builder()
                .image(image)
                .cmd(cmd)
                .build();

        boolean isImagePulled = pullImage(taskId, image);

        if (isImagePulled) {
            final Volume volume = createVolume(workerConfigurationService.getWorkerVolumeName() + "-" + taskId);
            metadataResult.setVolumeName(volume.name());
            metadataResult.setVolumeMountPoint(volume.mountpoint());

            final HostConfig hostConfig = createHostConfig(workerConfigurationService.getWorkerVolumeName() + "-" + taskId, REMOTE_PATH);
            final ContainerConfig containerConfig = createContainerConfig(image, cmd, hostConfig);

            String containerId = startContainer(taskId, containerConfig);
            if (containerId != null) {
                metadataResult.setContainerId(containerId);
                boolean isExecutionDone = waitContainerForExitStatus(taskId, containerId);
                if (!isExecutionDone) {
                    metadataResult.setMessage("Computation failed");
                }
            } else {
                metadataResult.setMessage("Unable to start container");
            }
        } else {
            metadataResult.setMessage("Unable to pull image");
        }
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

    private Volume createVolume(String volumeName) {
        Volume toCreate = null;

        if (volumeName != null) {
            toCreate = Volume.builder()
                    .name(volumeName)
                    .driver("local")
                    .build();
            try {
                return docker.createVolume(toCreate);
            } catch (DockerException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        return toCreate;
    }

    private HostConfig createHostConfig(String from, String to) {
        return HostConfig.builder()
                .appendBinds(HostConfig.Bind.from(from)
                        .to(to)
                        .readOnly(false)
                        .build())
                .build();
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

    private String startContainer(String taskId, ContainerConfig containerConfig) {
        String id = null;
        try {
            ContainerCreation creation = docker.createContainer(containerConfig);
            id = creation.id();
            if (id != null) {
                docker.startContainer(id);
                log.error("Computation start completed [taskId:{}, image:{}, cmd:{}]",
                        taskId, containerConfig.image(), containerConfig.cmd());
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Computation start failed [taskId:{}, image:{}, cmd:{}]",
                    taskId, containerConfig.image(), containerConfig.cmd());
            removeContainer(id);
            id = null;
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

    public ResultModel getResultModelWithZip(String taskId) {
        MetadataResult metadataResult = metadataResultMap.get(taskId);
        byte[] zipResultAsBytes = getResultZipAsBytes(metadataResult);

        removeContainer(metadataResult.getContainerId());
        removeVolume(metadataResult.getVolumeName());

        return ResultModel.builder()
                .taskId(taskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .zip(zipResultAsBytes).build();
    }

    private void removeContainer(String containerId) {
        if (containerId != null) {
            try {
                docker.removeContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void removeVolume(String volumeName) {
        if (volumeName != null) {
            try {
                docker.removeVolume(volumeName);
            } catch (DockerException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] getResultZipAsBytes(MetadataResult metadataResult) {
        byte[] zipResultAsBytes;
        String folderPathToZip = metadataResult.getVolumeMountPoint();
        String dockerLogs;
        if (metadataResult.getMessage() != null) {
            dockerLogs = metadataResult.getMessage();
        } else if (metadataResult.getContainerId() != null) {
            dockerLogs = getDockerLogs(metadataResult.getContainerId());
        } else {
            dockerLogs = "Unknown error";
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        try {
            addResultFilesToZipOutputStream(folderPathToZip, zos);
            addStdoutFileToZipOutputStream(dockerLogs, zos);
            zos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        zipResultAsBytes = baos.toByteArray();
        return zipResultAsBytes;
    }

    private String getDockerLogs(String id) {
        String logs = null;
        if (id != null) {
            try {
                logs = docker.logs(id, DockerClient.LogsParam.stdout(), DockerClient.LogsParam.stderr()).readFully();
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to get logs of computation [containerId:{}]", id);
            }
        }
        return logs;
    }

    private void addResultFilesToZipOutputStream(String pathToZip, ZipOutputStream zos) throws IOException {
        if (pathToZip != null) {
            Path sourceFolderPath = Paths.get(pathToZip);
            Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
                    log.info("Skipped visit {}", path.getFileName());
                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    log.info("Visited {}", file.getFileName());
                    zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

            });
        }
    }

    private void addStdoutFileToZipOutputStream(String stdout, ZipOutputStream zos) throws IOException {
        byte[] buffer = new byte[1024];
        zos.putNextEntry(new ZipEntry("stdout.txt"));
        InputStream fis = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
        int length;
        while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }
        zos.closeEntry();
        fis.close();
    }

    @PreDestroy
    public void onPreDestroy() {
        docker.close();
    }

}
