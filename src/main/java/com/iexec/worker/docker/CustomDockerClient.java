package com.iexec.worker.docker;

import com.iexec.worker.utils.WorkerConfigurationService;
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

@Slf4j
@Service
public class CustomDockerClient {

    public static final String EXITED = "exited";
    protected static final String DOCKER_BASE_VOLUME_NAME = "iexec-worker";
    private static final String REMOTE_PATH = "/iexec";
    private DefaultDockerClient docker;
    private WorkerConfigurationService configurationService;

    public CustomDockerClient(WorkerConfigurationService configurationService) throws DockerCertificateException {
        this.configurationService = configurationService;
        docker = DefaultDockerClient.fromEnv().build();
    }

    static HostConfig createHostConfig(Volume from) {
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

    static ContainerConfig createContainerConfig(String imageWithTag, String cmd, HostConfig hostConfig) {
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
        } catch (DockerException | InterruptedException e) {
            log.error("Image pull failed [taskId:{}, image:{}]", taskId, image);
            return false;
        }
        return true;
    }

    Volume createVolume(String taskId) {
        String volumeName = getTaskVolumeName(taskId);
        Volume toCreate = null;
        if (!volumeName.isEmpty()) {
            toCreate = Volume.builder()
                    .name(volumeName)
                    .driver("local")
                    .build();
            try {
                return docker.createVolume(toCreate);
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to create volume [taskId:{}, volumeName:{}]", taskId, volumeName);
            }
        }
        return toCreate;
    }

    String getTaskVolumeName(String taskId) {
        if (!taskId.isEmpty() && !configurationService.getWorkerName().isEmpty()) {
            return DOCKER_BASE_VOLUME_NAME + "-" + configurationService.getWorkerName() + "-" + taskId;
        }
        return "";
    }

    String startContainer(String taskId, ContainerConfig containerConfig) {
        if (containerConfig==null){
            return "";
        }
        String id = "";
        try {
            ContainerCreation creation = docker.createContainer(containerConfig);
            id = creation.id();
            if (id != null && !id.isEmpty()) {
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

    boolean waitContainerForExitStatus(String taskId, String containerId) {
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

    String getDockerLogs(String id) {
        String logs = "";
        if (!id.isEmpty()) {
            try {
                logs = docker.logs(id, com.spotify.docker.client.DockerClient.LogsParam.stdout(), com.spotify.docker.client.DockerClient.LogsParam.stderr()).readFully();
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to get logs of computation [containerId:{}]", id);
                logs = "Failed to get logs of computation";
            }
        }
        return logs;
    }

    InputStream getContainerResultArchive(String containerId) {
        InputStream containerResultArchive = null;
        try {
            containerResultArchive = docker.archiveContainer(containerId, REMOTE_PATH);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container archive [containerId:{}]", containerId);
        }
        return containerResultArchive;
    }

    boolean removeContainer(String containerId) {
        if (!containerId.isEmpty()) {
            try {
                docker.removeContainer(containerId);
                log.debug("Removed container [containerId:{}]", containerId);
                return true;
            } catch (DockerException | InterruptedException e) {
                log.error("Failed to remove container [containerId:{}]", containerId);
            }
        }
        return false;
    }

    boolean removeVolume(String taskId) {
        String volumeName = getTaskVolumeName(taskId);
        if (!taskId.isEmpty()) {
            try {
                docker.removeVolume(volumeName);
                log.debug("Removed volume [volumeName:{}]", volumeName);
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
