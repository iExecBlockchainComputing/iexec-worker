package com.iexec.worker.docker;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.utils.WorkerConfigurationService;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.iexec.worker.docker.CustomDockerClient.getContainerConfig;
import static com.iexec.worker.utils.FileHelper.*;

@Slf4j
@Service
public class DockerComputationService {

    private static final String DETERMINIST_FILE_NAME = "consensus.iexec";

    private static final String STDOUT_FILENAME = "stdout.txt";

    private final CustomDockerClient dockerClient;
    private WorkerConfigurationService configurationService;

    public DockerComputationService(CustomDockerClient dockerClient, WorkerConfigurationService configurationService) {
        this.dockerClient = dockerClient;
        this.configurationService = configurationService;
    }

    public MetadataResult dockerRun(String taskId, String image, String cmd) {
        //TODO: check image equals image:tag
        MetadataResult metadataResult = MetadataResult.builder()
                .image(image)
                .cmd(cmd)
                .build();

        if (dockerClient.pullImage(taskId, image)) {
            String volumeName = dockerClient.createVolume(taskId);
            ContainerConfig containerConfig = getContainerConfig(image, cmd, volumeName);
            startComputation(taskId, metadataResult, containerConfig);
        } else {
            createStdoutFile(taskId, "Failed to pull image");
        }

        zipTaskResult(configurationService.getResultBaseDir(), taskId);

        String hash = computeDeterministHash(taskId);
        metadataResult.setDeterministHash(hash);

        return metadataResult;
    }

    private String computeDeterministHash(String taskId) {
        String deterministFilePath = configurationService.getResultBaseDir() + "/" + taskId + "/iexec/" + DETERMINIST_FILE_NAME;
        try {
            byte[] content = Files.readAllBytes(Paths.get(deterministFilePath));
            return BytesUtils.bytesToString(Hash.sha3(content));
        } catch (IOException e) {
            log.error("The determinist hash couldn't be computed [taskId:{}, exception:{}]", taskId, e.getMessage());
        }
        return "";
    }

    private void startComputation(String taskId, MetadataResult metadataResult, ContainerConfig containerConfig) {
        String containerId = dockerClient.startContainer(taskId, containerConfig);
        if (!containerId.isEmpty()) {
            metadataResult.setContainerId(containerId);

            waitForComputation(taskId);
            copyComputationResults(taskId);

            dockerClient.removeContainer(taskId);
            dockerClient.removeVolume(taskId);
        } else {
            createStdoutFile(taskId, "Failed to start container");
        }
    }

    private void waitForComputation(String taskId) {
        boolean executionDone = dockerClient.waitContainer(taskId);

        if (executionDone) {
            String dockerLogs = dockerClient.getContainerLogs(taskId);
            createStdoutFile(taskId, dockerLogs);
        } else {
            createStdoutFile(taskId, "Computation failed");
        }
    }

    private void copyComputationResults(String taskId) {
        InputStream containerResult = dockerClient.getContainerResultArchive(taskId);
        copyResultToTaskFolder(containerResult, configurationService.getResultBaseDir(), taskId);
    }

    private File createStdoutFile(String taskId, String stdoutContent) {
        log.info("Stdout file added to result folder [taskId:{}]", taskId);
        return createFileWithContent(configurationService.getResultBaseDir() + "/" + taskId, STDOUT_FILENAME, stdoutContent);
    }

}
