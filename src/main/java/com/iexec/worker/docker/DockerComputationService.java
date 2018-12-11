package com.iexec.worker.docker;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.messages.ContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;

import static com.iexec.worker.docker.CustomDockerClient.getContainerConfig;
import static com.iexec.worker.utils.FileHelper.createFileWithContent;

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

    public MetadataResult dockerRun(String chainTaskId, String image, String cmd, Date maxExecutionTime) throws IOException {
        //TODO: check image equals image:tag
        String containerId = "";
        if (dockerClient.isImagePulled(image)) {
            String volumeName = dockerClient.createVolume(chainTaskId);
            ContainerConfig containerConfig = getContainerConfig(image, cmd, volumeName);
            containerId = startComputation(chainTaskId, containerConfig, maxExecutionTime);
        } else {
            createStdoutFile(chainTaskId, "Failed to pull image");
        }

        File zipFile = FileHelper.zipFolder(resultService.getResultFolderPath(chainTaskId));
        log.info("Zip file has been created [chainTaskId:{}, zipFile:{}]", chainTaskId, zipFile.getAbsolutePath());

        String hash = computeDeterministHash(chainTaskId);
        log.info("Determinist Hash has been computed [chainTaskId:{}, deterministHash:{}]", chainTaskId, hash);

        return MetadataResult.builder()
                .image(image)
                .cmd(cmd)
                .deterministHash(hash)
                .containerId(containerId)
                .build();
    }

    public boolean dockerPull(String chainTaskId, String image) {
        return dockerClient.pullImage(chainTaskId, image);
    }

    private String startComputation(String chainTaskId, ContainerConfig containerConfig, Date maxExecutionTime) {
        String containerId = dockerClient.startContainer(chainTaskId, containerConfig);
        if (!containerId.isEmpty()) {
            Date executionTimeout = Date.from(Instant.now().plusMillis(maxExecutionTime.getTime()));
            waitForComputation(chainTaskId, executionTimeout);
            copyComputationResults(chainTaskId);

            dockerClient.removeContainer(chainTaskId);
            dockerClient.removeVolume(chainTaskId);
        } else {
            createStdoutFile(chainTaskId, "Failed to start container");
        }
        return containerId;
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


    private void waitForComputation(String chainTaskId, Date executionTimeout) {
        boolean executionDone = dockerClient.waitContainer(chainTaskId, executionTimeout);

        if (executionDone) {
            String dockerLogs = dockerClient.getContainerLogs(chainTaskId);
            createStdoutFile(chainTaskId, dockerLogs);
        } else {
            createStdoutFile(chainTaskId, "Computation failed");
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
                File curfile = new File(resultBaseDirectory + File.separator + chainTaskId, entry.getName());
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

    private File createStdoutFile(String chainTaskId, String stdoutContent) {
        log.info("Stdout file added to result folder [chainTaskId:{}]", chainTaskId);
        String filePath = resultService.getResultFolderPath(chainTaskId) + File.separator + STDOUT_FILENAME;
        return createFileWithContent(filePath, stdoutContent);
    }
}
