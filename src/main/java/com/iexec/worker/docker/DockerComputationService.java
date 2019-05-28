package com.iexec.worker.docker;

import com.iexec.common.replicate.AvailableReplicateModel;
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

    public boolean dockerPull(String chainTaskId, String image) {
        return dockerClient.pullImage(chainTaskId, image);
    }

    public String dockerRunAndGetLogs(AvailableReplicateModel replicateModel, String datasetFilename) {
        String chainTaskId = replicateModel.getContributionAuthorization().getChainTaskId();
        String image = replicateModel.getAppUri();
        //TODO: check image equals image:tag
        String stdout = "";

        if (!dockerClient.isImagePulled(image)) {
            return stdout;
        }

        String hostBaseVolume = configurationService.getTaskBaseDir(chainTaskId);
        ContainerConfig containerConfig;

        if (replicateModel.isTrustedExecution()) {
            containerConfig = getContainerConfig(image, replicateModel.getCmd(), hostBaseVolume,
                    TEE_DOCKER_ENV_CHAIN_TASKID + "=" + chainTaskId,
                    TEE_DOCKER_ENV_WORKER_ADDRESS + "=" + configurationService.getWorkerWalletAddress(),
                    DATASET_FILENAME + "=" + datasetFilename);
        } else {
            containerConfig = getContainerConfig(image, replicateModel.getCmd(), hostBaseVolume,
                    DATASET_FILENAME + "=" + datasetFilename);
        }

        stdout = startComputationAndGetLogs(chainTaskId, containerConfig, replicateModel.getMaxExecutionTime());

        return stdout;
    }

    private String startComputationAndGetLogs(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
        String stdout = "";
        String containerId = dockerClient.startContainer(chainTaskId, containerConfig);

        if (containerId.isEmpty()) return stdout;

        Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
        boolean executionDone = dockerClient.waitContainer(chainTaskId, executionTimeoutDate);

        stdout = executionDone ? dockerClient.getContainerLogs(chainTaskId) : "Computation failed";

        dockerClient.removeContainer(chainTaskId);
        return stdout;
    }
}
