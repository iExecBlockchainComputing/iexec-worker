package com.iexec.worker.docker;

import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

import static com.iexec.worker.docker.CustomDockerClient.getContainerConfig;

@Slf4j
@Service
public class DockerComputationService {

    private static final String DATASET_FILENAME = "DATASET_FILENAME";
    private static final String TEE_DOCKER_ENV_CHAIN_TASKID = "TASKID";
    private static final String TEE_DOCKER_ENV_WORKER_ADDRESS = "WORKER";

    private final CustomDockerClient dockerClient;
    private final WorkerConfigurationService configurationService;

    public DockerComputationService(CustomDockerClient dockerClient,
                                    WorkerConfigurationService configurationService) {
        this.dockerClient = dockerClient;
        this.configurationService = configurationService;
    }

    public String dockerRunAndGetLogs(TaskDescription taskDescription, String datasetFilename) {
        String chainTaskId = taskDescription.getChainTaskId();
        String image = taskDescription.getAppUri();
        //TODO: check image equals image:tag
        String stdout = "";

        if (!dockerClient.isImagePulled(image)) {
            return stdout;
        }

        String hostBaseVolume = configurationService.getTaskBaseDir(chainTaskId);
        ContainerConfig containerConfig;

        if (taskDescription.isTrustedExecution()) {
            containerConfig = getContainerConfig(image, taskDescription.getCmd(), hostBaseVolume,
                    TEE_DOCKER_ENV_CHAIN_TASKID + "=" + chainTaskId,
                    TEE_DOCKER_ENV_WORKER_ADDRESS + "=" + configurationService.getWorkerWalletAddress(),
                    DATASET_FILENAME + "=" + datasetFilename);
        } else {
            containerConfig = getContainerConfig(image, taskDescription.getCmd(), hostBaseVolume,
                    DATASET_FILENAME + "=" + datasetFilename);
        }

        stdout = startComputationAndGetLogs(chainTaskId, containerConfig, taskDescription.getMaxExecutionTime());

        return stdout;
    }

    public boolean dockerPull(String chainTaskId, String image) {
        return dockerClient.pullImage(chainTaskId, image);
    }

    private String startComputationAndGetLogs(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
        String stdout = "";
        String containerId = dockerClient.startContainer(chainTaskId, containerConfig);

        if (containerId.isEmpty()) {
            return stdout;
        }

        Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
        stdout = waitForComputationAndGetLogs(chainTaskId, executionTimeoutDate);
        dockerClient.removeContainer(chainTaskId);

        return stdout;
    }

    private String waitForComputationAndGetLogs(String chainTaskId, Date executionTimeoutDate) {
        boolean executionDone = dockerClient.waitContainer(chainTaskId, executionTimeoutDate);
        if (executionDone) {
            return dockerClient.getContainerLogs(chainTaskId);
        } else {
            return "Computation failed";
        }
    }

}
