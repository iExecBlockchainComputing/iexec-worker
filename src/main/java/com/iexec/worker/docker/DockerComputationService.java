package com.iexec.worker.docker;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.result.MetadataResult;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.iexec.worker.docker.CustomDockerClient.getContainerConfig;
import static com.iexec.worker.utils.FileHelper.*;

@Slf4j
@Service
public class DockerComputationService {

    private static final String DETERMINIST_FILE_NAME = "consensus.iexec";
    private static final String STDOUT_FILENAME = "stdout.txt";

    private final CustomDockerClient dockerClient;
    private final WorkerConfigurationService configurationService;

    public DockerComputationService(CustomDockerClient dockerClient,
                                    WorkerConfigurationService configurationService) {
        this.dockerClient = dockerClient;
        this.configurationService = configurationService;
    }

    public MetadataResult dockerRun(String taskId, String image, String cmd) throws IOException {
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

        String folderToZip = configurationService.getResultBaseDir() + "/" + taskId;
        zipTaskResult(folderToZip);

        String hash = computeDeterministHash(taskId);
        log.info("Determinist Hash has been computed [chainTaskId:{}, deterministHash:{}]", taskId, hash);
        metadataResult.setDeterministHash(hash);

        return metadataResult;
    }

    private String computeDeterministHash(String taskId) throws IOException {
        String deterministFilePathName = configurationService.getResultBaseDir() + "/" + taskId + "/iexec/" + DETERMINIST_FILE_NAME;
        Path deterministFilePath = Paths.get(deterministFilePathName);

        if (deterministFilePath.toFile().exists()) {
            byte[] content = Files.readAllBytes(deterministFilePath);
            String hash = BytesUtils.bytesToString(Hash.sha3(content));
            log.info("The determinist file exists and its hash has been computed [taskId:{}, hash:{}]", taskId, hash);
            return hash;
        } else {
            log.info("No determinist file exists [taskId:{}]", taskId);
        }

        String resultFilePathName = configurationService.getResultBaseDir() + "/" + taskId + ".zip";
        byte[] content = Files.readAllBytes(Paths.get(resultFilePathName));
        String hash = BytesUtils.bytesToString(Hash.sha3(content));
        log.info("The hash of the result file will be used instead [taskId:{}, hash:{}]", taskId, hash);
        return hash;
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
        String folderPath = configurationService.getResultBaseDir() + "/" + taskId;
        return createFileWithContent(folderPath, STDOUT_FILENAME, stdoutContent);
    }

}
