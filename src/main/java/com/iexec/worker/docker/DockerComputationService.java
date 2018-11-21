package com.iexec.worker.docker;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
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
    private final ResultService resultService;

    public DockerComputationService(CustomDockerClient dockerClient,
                                    WorkerConfigurationService configurationService,
                                    ResultService resultService) {
        this.dockerClient = dockerClient;
        this.configurationService = configurationService;
        this.resultService = resultService;
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

        zipFolder(resultService.getResultFolderPath(taskId));

        String hash = computeDeterministHash(taskId);
        log.info("Determinist Hash has been computed [chainTaskId:{}, deterministHash:{}]", taskId, hash);
        metadataResult.setDeterministHash(hash);

        return metadataResult;
    }

    private String computeDeterministHash(String chainTaskId) throws IOException {
        String deterministFilePathName = resultService.getResultFolderPath(chainTaskId) + "/iexec/" + DETERMINIST_FILE_NAME;
        Path deterministFilePath = Paths.get(deterministFilePathName);

        if (deterministFilePath.toFile().exists()) {
            byte[] content = Files.readAllBytes(deterministFilePath);
            String hash = BytesUtils.bytesToString(Hash.sha3(content));
            log.info("The determinist file exists and its hash has been computed [chainTaskId:{}, hash:{}]", chainTaskId, hash);
            return hash;
        } else {
            log.info("No determinist file exists [chainTaskId:{}]", chainTaskId);
        }

        String resultFilePathName = resultService.getResultZipFilePath(chainTaskId);
        byte[] content = Files.readAllBytes(Paths.get(resultFilePathName));
        String hash = BytesUtils.bytesToString(Hash.sha3(content));
        log.info("The hash of the result file will be used instead [chainTaskId:{}, hash:{}]", chainTaskId, hash);
        return hash;
    }

    private void startComputation(String chainTaskId, MetadataResult metadataResult, ContainerConfig containerConfig) {
        String containerId = dockerClient.startContainer(chainTaskId, containerConfig);
        if (!containerId.isEmpty()) {
            metadataResult.setContainerId(containerId);

            waitForComputation(chainTaskId);
            copyComputationResults(chainTaskId);

            dockerClient.removeContainer(chainTaskId);
            dockerClient.removeVolume(chainTaskId);
        } else {
            createStdoutFile(chainTaskId, "Failed to start container");
        }
    }

    private void waitForComputation(String chainTaskId) {
        boolean executionDone = dockerClient.waitContainer(chainTaskId);

        if (executionDone) {
            String dockerLogs = dockerClient.getContainerLogs(chainTaskId);
            createStdoutFile(chainTaskId, dockerLogs);
        } else {
            createStdoutFile(chainTaskId, "Computation failed");
        }
    }

    private void copyComputationResults(String chainTaskId) {
        InputStream containerResult = dockerClient.getContainerResultArchive(chainTaskId);
        copyResultToTaskFolder(containerResult, configurationService.getResultBaseDir(), chainTaskId);
    }

    private File createStdoutFile(String chainTaskId, String stdoutContent) {
        log.info("Stdout file added to result folder [chainTaskId:{}]", chainTaskId);
        String filePath = resultService.getResultFolderPath(chainTaskId) + "/" + STDOUT_FILENAME;
        return createFileWithContent(filePath, stdoutContent);
    }

}
