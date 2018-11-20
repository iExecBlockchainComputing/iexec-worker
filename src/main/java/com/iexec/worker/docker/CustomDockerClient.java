package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
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
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CustomDockerClient {

    public static final String EXITED = "exited";
    protected static final String DOCKER_BASE_VOLUME_NAME = "iexec-worker";
    private static final String REMOTE_PATH = "/iexec";
    private DefaultDockerClient docker;
    private WorkerConfigurationService configurationService;

    private Map<String, String> taskToContainerId;

    public CustomDockerClient(WorkerConfigurationService configurationService) throws DockerCertificateException {
        this.configurationService = configurationService;
        docker = DefaultDockerClient.fromEnv().build();
        taskToContainerId = new ConcurrentHashMap<>();
    }

    private static HostConfig getHostConfig(String from) {
        if (from != null) {
            return HostConfig.builder()
                    .appendBinds(HostConfig.Bind.from(from)
                            .to(REMOTE_PATH)
                            .readOnly(false)
                            .build())
                    .build();
        }
        return null;
    }

    static ContainerConfig getContainerConfig(String imageWithTag, String cmd, String volumeName) {
        HostConfig hostConfig = getHostConfig(volumeName);

        if (imageWithTag.isEmpty() || hostConfig == null) {
            return null;
        }
        ContainerConfig.Builder builder = ContainerConfig.builder()
                .image(imageWithTag)
                .hostConfig(hostConfig);
        if (cmd == null || cmd.isEmpty()) {
            return builder.build();
        } else {
            return builder.cmd(cmd).build();
        }
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

    String createVolume(String taskId) {
        String volumeName = getVolumeName(taskId);
        if (!volumeName.isEmpty()) {
            Volume toCreate = Volume.builder()
                    .name(volumeName)
                    .driver("local")
                    .build();
            try {
                Volume createdVolume = docker.createVolume(toCreate);
                log.debug("Created volume [taskId:{}, volumeName:{}]", taskId, volumeName);
                return createdVolume.name();
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to create volume [taskId:{}, volumeName:{}]", taskId, volumeName);
            }
        }
        return "";
    }

    String getVolumeName(String taskId) {
        if (!taskId.isEmpty() && !configurationService.getWorkerName().isEmpty()) {
            return DOCKER_BASE_VOLUME_NAME + "-" + configurationService.getWorkerName() + "-" + taskId;
        }
        return "";
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

    boolean waitContainer(String taskId) {
        String containerId = getContainerId(taskId);
        //TODO: add category timeout
        boolean isExecutionDone = false;
        try {
            while (!docker.inspectContainer(containerId).state().status().equals(EXITED)) {
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

    InputStream getContainerResultArchive(String taskId) {
        String containerId = getContainerId(taskId);
        InputStream containerResultArchive = null;
        try {
            containerResultArchive = docker.archiveContainer(containerId, REMOTE_PATH);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container archive [taskId:{}, containerId:{}]",
                    taskId, containerId);
        }
        return containerResultArchive;
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

    boolean removeVolume(String taskId) {
        String volumeName = getVolumeName(taskId);
        if (!taskId.isEmpty()) {
            try {
                docker.removeVolume(volumeName);
                log.debug("Removed volume [taskId:{}, volumeName:{}]", taskId, volumeName);
                return true;
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to remove volume [taskId:{}, volumeName:{}]", taskId, volumeName);
            }
        }
        return false;
    }

    @PreDestroy
    void onPreDestroy() {
        docker.close();
    }

}
