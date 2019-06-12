package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.Device;
import com.spotify.docker.client.messages.HostConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CustomDockerClient {

    private static final String EXITED = "exited";

    private DefaultDockerClient docker;
    private Map<String, String> taskToContainerId;
    private WorkerConfigurationService workerConfigurationService;

    public CustomDockerClient(WorkerConfigurationService workerConfigurationService) throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();
        taskToContainerId = new ConcurrentHashMap<>();
        this.workerConfigurationService = workerConfigurationService;
    }

    public HostConfig getHostConfig(String chainTaskId) {
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);

        if (inputBind == null || outputBind == null) return null;

        return HostConfig.builder()
                .appendBinds(inputBind, outputBind)
                .build();
    }

    public HostConfig getSconeHostConfig(String chainTaskId) {
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);
        HostConfig.Bind sconeBind = createSconeBind(chainTaskId);

        if (inputBind == null || outputBind == null || sconeBind == null) return null;

        Device device = Device.builder()
                .pathOnHost("/dev/isgx")
                .pathInContainer("/dev/isgx")
                .cgroupPermissions("rwm")
                .build();

        return HostConfig.builder()
                .appendBinds(inputBind, outputBind, sconeBind)
                .devices(device)
                .build();
    }

    private HostConfig.Bind createInputBind(String chainTaskId) {
        String inputMountPoint = workerConfigurationService.getTaskInputDir(chainTaskId);
        return createBind(inputMountPoint, FileHelper.SLASH_IEXEC_IN);
    }

    private HostConfig.Bind createOutputBind(String chainTaskId) {
        String outputMountPoint = workerConfigurationService.getTaskIexecOutDir(chainTaskId);
        return createBind(outputMountPoint, FileHelper.SLASH_IEXEC_OUT);
    }

    private HostConfig.Bind createSconeBind(String chainTaskId) {
        String sconeMountPoint = workerConfigurationService.getTaskSconeDir(chainTaskId);
        return createBind(sconeMountPoint, FileHelper.SLASH_SCONE);
    }

    private HostConfig.Bind createBind(String source, String dest) {
        if (source == null || source.isEmpty() || dest == null || dest.isEmpty()) return null;

        boolean isSourceMountPointSet = FileHelper.createFolder(source);

        if (!isSourceMountPointSet) {
            log.error("Source mount point doesn't exist [SourceMountPoint:{}]", source);
            return null;
        }

        return HostConfig.Bind.from(source)
                .to(dest)
                .readOnly(false)
                .build();
    }

    public ContainerConfig buildContainerConfig(String chainTaskId, String imageUri, String cmd, String... env) {
        if (imageUri == null || imageUri.isEmpty()) return null;
        HostConfig hostConfig = getHostConfig(chainTaskId);
        return buildContainerConfig(hostConfig, imageUri, cmd, env);
    }

    public ContainerConfig buildContainerConfig(HostConfig hostConfig, String imageUri, String cmd, String... env) {
        if (hostConfig == null || imageUri == null || imageUri.isEmpty()) return null;

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();

        if (cmd != null && !cmd.isEmpty()) containerConfigBuilder.cmd(cmd);

        return containerConfigBuilder.image(imageUri)
                .hostConfig(hostConfig)
                .env(env)
                .build();
    }

    // public static ContainerConfig buildContainerConfig(String imageWithTag, String cmd, String hostBaseVolume, String... env) {
    //     HostConfig hostConfig = getHostConfig(hostBaseVolume);

    //     if (imageWithTag.isEmpty() || hostConfig == null) return null;

    //     ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder()
    //             .image(imageWithTag)
    //             .hostConfig(hostConfig);

    //     if (cmd != null && !cmd.isEmpty()) containerConfigBuilder.cmd(cmd);

    //     return containerConfigBuilder.env(env).build();
    // }

    boolean pullImage(String chainTaskId, String image) {
        log.info("Image pull started [chainTaskId:{}, image:{}]", chainTaskId, image);

        try {
            docker.pull(image);
        } catch (DockerException | InterruptedException e) {
            log.error("Image pull failed [chainTaskId:{}, image:{}]", chainTaskId, image);
            e.printStackTrace();
            return false;
        }

        log.info("Image pull completed [chainTaskId:{}, image:{}]", chainTaskId, image);
        return true;
    }

    public boolean isImagePulled(String image) {
        try {
            return !docker.inspectImage(image).id().isEmpty();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to check if image was pulled [image:{}]", image);
            e.printStackTrace();
            return false;
        }
    }

    String getContainerId(String taskId) {
        return taskToContainerId.containsKey(taskId) ? taskToContainerId.get(taskId) : "";
    }

    // void mapContainerIdToChainTaskId() {}

    /**
     * This creates a container, maps its id to the taskId,
     * starts the container and returns logs
     */
    public String dockerRun(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
        // docker create
        String containerId = createContainer(chainTaskId, containerConfig);
        if (containerId.isEmpty()) return "";

        // docker start
        boolean isContainerStarted = startContainer(chainTaskId, containerId);

        if (!isContainerStarted) return "";

        Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
        boolean executionDone = waitContainer(chainTaskId, executionTimeoutDate);

        // docker logs
        String stdout = executionDone ? getContainerLogs(chainTaskId) : "Computation failed";

        removeContainer(containerId);
        return stdout;
    }

    private String createContainer(String chainTaskId, ContainerConfig containerConfig) {
        ContainerCreation containerCreation;

        try {
            containerCreation = docker.createContainer(containerConfig);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to create container [chainTaskId:{}, image:{}, cmd:{}]",
                    chainTaskId, containerConfig.image(), containerConfig.cmd());
            e.printStackTrace();
            return "";
        }

        if (containerCreation == null) return "";

        return containerCreation.id() != null ? containerCreation.id() : "";
    }

    private boolean startContainer(String chainTaskId, String containerId) {
        log.info("Starting container [chainTaskId:{}, containerId:{}]", chainTaskId, containerId);

        try {
            docker.startContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to start container [chainTaskId:{}, containerId:{}]",
                    chainTaskId, containerId);
            e.printStackTrace();
            removeContainer(containerId);
            return false;
        }

        log.info("Started container [chainTaskId:{}, containerId:{}]", chainTaskId, containerId);
        taskToContainerId.put(chainTaskId, containerId);
        return true;
    }

    // private String startComputationAndGetLogs(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
    //     String stdout = "";
    //     String containerId = startContainer(chainTaskId, containerConfig);

    //     if (containerId.isEmpty()) return stdout;

    //     Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
    //     boolean executionDone = dockerClient.waitContainer(chainTaskId, executionTimeoutDate);

    //     stdout = executionDone ? dockerClient.getContainerLogs(chainTaskId) : "Computation failed";

    //     dockerClient.removeContainer(chainTaskId);
    //     return stdout;
    // }

    // String startContainer(String taskId, ContainerConfig containerConfig) {
    //     String containerId = "";

    //     if (containerConfig == null) return containerId;

    //     try {
    //         ContainerCreation creation = docker.createContainer(containerConfig);
    //         containerId = creation.id();
    //         if (containerId != null && !containerId.isEmpty()) {
    //             //TODO check image his here
    //             docker.startContainer(containerId);
    //             log.info("Computation started [taskId:{}, image:{}, cmd:{}]",
    //                     taskId, containerConfig.image(), containerConfig.cmd());
    //         }
    //     } catch (DockerException | InterruptedException e) {
    //         log.error("Computation failed to start[taskId:{}, image:{}, cmd:{}]",
    //                 taskId, containerConfig.image(), containerConfig.cmd());
    //         e.printStackTrace();
    //         removeContainer(taskId);
    //         containerId = "";
    //     }
    //     taskToContainerId.put(taskId, containerId);
    //     return containerId;
    // }

    boolean waitContainer(String taskId, Date executionTimeoutDate) {
        String containerId = getContainerId(taskId);
        boolean isExecutionDone = false;
        try {
            while (true) {
                boolean isComputed = docker.inspectContainer(containerId).state().status().equals(EXITED);
                boolean isTimeout = new Date().after(executionTimeoutDate);
                Thread.sleep(1000);
                log.info("Computation running [taskId:{}, containerId:{}, status:{}, isComputed:{}, isTimeout:{}]",
                        taskId, containerId, docker.inspectContainer(containerId).state().status(), isComputed, isTimeout);
                if (isComputed || isTimeout) {
                    docker.stopContainer(containerId, 0);
                    break;
                }
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

    String getContainerLogs(String taskId) {
        String containerId = getContainerId(taskId);
        if (!containerId.isEmpty()) {
            try {
                return docker.logs(containerId, com.spotify.docker.client.DockerClient.LogsParam.stdout(), com.spotify.docker.client.DockerClient.LogsParam.stderr()).readFully();
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to get logs of computation [taskId:{}, containerId:{}]",
                        taskId, containerId);
            }
        }
        return "Failed to get logs of computation";
    }

    boolean removeContainer(String containerId) {
        log.debug("Removing container [containerId:{}]", containerId);
        try {
            docker.removeContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to remove container [containerId:{}]", containerId);
            return false;
        }

        log.debug("Removed container [containerId:{}]", containerId);
        return true;
    }

    @PreDestroy
    void onPreDestroy() {
        docker.close();
    }

}
