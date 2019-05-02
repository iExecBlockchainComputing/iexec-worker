package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Volume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CustomDockerClient {

    private static final String EXITED = "exited";
    private static final String DOCKER_BASE_VOLUME_NAME = "iexec-worker";

    private DefaultDockerClient docker;
    private WorkerConfigurationService configurationService;

    private Map<String, String> taskToContainerId;

    public CustomDockerClient(WorkerConfigurationService configurationService) throws DockerCertificateException {
        this.configurationService = configurationService;
        docker = DefaultDockerClient.fromEnv().build();
        taskToContainerId = new ConcurrentHashMap<>();
    }

    private static HostConfig getHostConfig(String hostBaseVolume) {
        if (hostBaseVolume != null && !hostBaseVolume.isEmpty()) {
            String outputMountpoint = hostBaseVolume + FileHelper.SLASH_OUTPUT + FileHelper.SLASH_IEXEC_OUT;
            String inputMountpoint = hostBaseVolume + FileHelper.SLASH_INPUT;
            FileHelper.createFolder(inputMountpoint);
            FileHelper.createFolder(outputMountpoint);

            boolean isInputMountpointSet = new File(inputMountpoint).exists();
            boolean isOutputMountpointSet = new File(outputMountpoint).exists();

            if (!(isInputMountpointSet && isOutputMountpointSet)) {
                log.error("inputMountpoint or outputMountpoint doesn't exists [isInputMountpointSet:{}, " +
                        "isOutputMountpointSet:{}]", isInputMountpointSet, isOutputMountpointSet);
                return null;
            }

            return HostConfig.builder()
                    .appendBinds(
                            HostConfig.Bind.from(inputMountpoint)
                                    .to(FileHelper.SLASH_IEXEC_IN)
                                    .readOnly(false)
                                    .build(),
                            HostConfig.Bind.from(outputMountpoint)
                                    .to(FileHelper.SLASH_IEXEC_OUT)
                                    .readOnly(false)
                                    .build()
                    )
                    .build();
        }
        return null;
    }

    private static ContainerConfig.Builder getContainerConfigBuilder(String imageWithTag, String cmd, String hostBaseVolume) {
        HostConfig hostConfig = getHostConfig(hostBaseVolume);

        if (imageWithTag.isEmpty() || hostConfig == null) {
            return null;
        }
        ContainerConfig.Builder builder = ContainerConfig.builder()
                .image(imageWithTag)
                .hostConfig(hostConfig);
        if (cmd == null || cmd.isEmpty()) {
            return builder;
        } else {
            return builder.cmd(cmd);
        }
    }

    static ContainerConfig getContainerConfig(String imageWithTag, String cmd, String hostBaseVolume, String... env) {
        ContainerConfig.Builder containerConfigBuilder = getContainerConfigBuilder(imageWithTag, cmd, hostBaseVolume);
        if (containerConfigBuilder != null) {
            return containerConfigBuilder.env(env).build();
        }
        return null;
    }

    boolean pullImage(String taskId, String image) {
        try {
            log.info("Image pull started [taskId:{}, image:{}]",
                    taskId, image);
            docker.pull(image);
            log.info("Image pull completed [taskId:{}, image:{}]",
                    taskId, image);
            return true;
        } catch (DockerException | InterruptedException e) {
            log.error("Image pull failed [taskId:{}, image:{}]", taskId, image);
        }
        return false;
    }

    boolean isImagePulled(String image) {
        try {
            return !docker.inspectImage(image).id().isEmpty();
        } catch (DockerException | InterruptedException e) {
            log.error("isImagePulled failed [image:{}]", image);
        }
        return false;
    }

    String getContainerId(String taskId) {
        if (taskToContainerId.containsKey(taskId)) {
            return taskToContainerId.get(taskId);
        }
        return "";
    }

    String startContainer(String taskId, ContainerConfig containerConfig) {
        String containerId = "";
        if (containerConfig == null) {
            return containerId;
        }
        try {
            ContainerCreation creation = docker.createContainer(containerConfig);
            containerId = creation.id();
            if (containerId != null && !containerId.isEmpty()) {
                //TODO check image his here
                docker.startContainer(containerId);
                log.info("Computation started [taskId:{}, image:{}, cmd:{}]",
                        taskId, containerConfig.image(), containerConfig.cmd());
            }
        } catch (DockerException | InterruptedException e) {
            log.error("Computation failed to start[taskId:{}, image:{}, cmd:{}]",
                    taskId, containerConfig.image(), containerConfig.cmd());
            removeContainer(taskId);
            containerId = "";
        }
        taskToContainerId.put(taskId, containerId);
        return containerId;
    }

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

    boolean removeContainer(String taskId) {
        String containerId = getContainerId(taskId);
        if (!containerId.isEmpty()) {
            try {
                docker.removeContainer(containerId);
                log.debug("Removed container [taskId:{}, containerId:{}]",
                        taskId, containerId);
                taskToContainerId.remove(taskId);
                return true;
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to remove container [taskId:{}, containerId:{}]",
                        taskId, containerId);
            }
        }
        return false;
    }

    @PreDestroy
    void onPreDestroy() {
        docker.close();
    }

}
