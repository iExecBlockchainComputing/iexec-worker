package com.iexec.worker.docker;

import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;

import static com.iexec.worker.docker.CustomDockerClient.getContainerConfig;

@Slf4j
@Service
public class DockerComputationService {

    private static final String TEE_DOCKER_ENV_CHAIN_TASKID = "TASKID";
    private static final String TEE_DOCKER_ENV_WORKER_ADDRESS = "WORKER";

    private final CustomDockerClient dockerClient;
    private final WorkerConfigurationService configurationService;

    public DockerComputationService(CustomDockerClient dockerClient,
                                    WorkerConfigurationService configurationService,
                                    ResultService resultService) {
        this.dockerClient = dockerClient;
        this.configurationService = configurationService;
    }

    public String dockerRunAndGetLogs(AvailableReplicateModel replicateModel) {
        String chainTaskId = replicateModel.getContributionAuthorization().getChainTaskId();
        String image = replicateModel.getAppUri();
        //TODO: check image equals image:tag
        String stdout = "";
        if (dockerClient.isImagePulled(image)) {
            String volumeNameOut = dockerClient.createVolume(chainTaskId);
            String volumeNameIn = configurationService.getResultBaseDir() + File.separator + chainTaskId + File.separator + FileHelper.INPUT_FOLDER_NAME;
            ContainerConfig containerConfig;

            if (replicateModel.isTrustedExecution()) {
                containerConfig = getContainerConfig(image, replicateModel.getCmd(), volumeNameOut, volumeNameIn,
                        TEE_DOCKER_ENV_CHAIN_TASKID + "=" + chainTaskId,
                        TEE_DOCKER_ENV_WORKER_ADDRESS + "=" + configurationService.getWorkerWalletAddress());
            } else {
                containerConfig = getContainerConfig(image, replicateModel.getCmd(), volumeNameOut, volumeNameIn);
            }

            stdout = startComputationAndGetLogs(chainTaskId, containerConfig, replicateModel.getMaxExecutionTime());
        }
        return stdout;
    }

    public boolean dockerPull(String chainTaskId, String image) {
        return dockerClient.pullImage(chainTaskId, image);
    }

    private String startComputationAndGetLogs(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
        String stdout = "";
        String containerId = dockerClient.startContainer(chainTaskId, containerConfig);
        if (!containerId.isEmpty()) {
            Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
            stdout = waitForComputationAndGetLogs(chainTaskId, executionTimeoutDate);
            copyComputationResults(chainTaskId);

            dockerClient.removeContainer(chainTaskId);
            dockerClient.removeVolume(chainTaskId);
        }
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

    private void copyComputationResults(String chainTaskId) {
        InputStream containerResultArchive = dockerClient.getContainerResultArchive(chainTaskId);
        String resultBaseDirectory = configurationService.getResultBaseDir();

        try {
            final TarArchiveInputStream tarStream = new TarArchiveInputStream(containerResultArchive);

            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                log.debug(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(resultBaseDirectory + File.separator + chainTaskId + File.separator + FileHelper.OUTPUT_FOLDER_NAME, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                IOUtils.copy(tarStream, new FileOutputStream(curfile));
            }
            log.info("Results from remote added to result folder [chainTaskId:{}]", chainTaskId);
        } catch (IOException e) {
            log.error("Failed to copy container results to disk [chainTaskId:{}]", chainTaskId);
        }
    }

}
