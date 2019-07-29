package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.task.TaskDescription;

import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.FileHelper;

import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.COMPUTE_FAILED;


@Slf4j
@Service
public class ComputationService {

    // env variables that will be injected in the container of a task computation
    private static final String IEXEC_DATASET_FILENAME_ENV_PROPERTY = "IEXEC_DATASET_FILENAME";
    private static final String IEXEC_BOT_TASK_INDEX_ENV_PROPERTY = "IEXEC_BOT_TASK_INDEX";
    private static final String IEXEC_BOT_SIZE_ENV_PROPERTY = "IEXEC_BOT_SIZE";
    private static final String IEXEC_BOT_FIRST_INDEX_ENV_PROPERTY = "IEXEC_BOT_FIRST_INDEX";
    private static final String IEXEC_NB_INPUT_FILES_ENV_PROPERTY = "IEXEC_NB_INPUT_FILES";
    private static final String IEXEC_INPUT_FILES_ENV_PROPERTY_PREFIX = "IEXEC_INPUT_FILE_NAME_";
    private static final String IEXEC_INPUT_FILES_FOLDER_ENV_PROPERTY = "IEXEC_INPUT_FILES_FOLDER";

    private SmsService smsService;
    private DataService dataService;
    private CustomDockerClient customDockerClient;
    private SconeTeeService sconeTeeService;
    private ResultService resultService;

    public ComputationService(SmsService smsService,
                              DataService dataService,
                              CustomDockerClient customDockerClient,
                              SconeTeeService sconeTeeService,
                              ResultService resultService) {

        this.smsService = smsService;
        this.dataService = dataService;
        this.customDockerClient = customDockerClient;
        this.sconeTeeService = sconeTeeService;

        this.resultService = resultService;
    }

    public boolean isValidAppType(String chainTaskId, DappType type) {
        if (type.equals(DappType.DOCKER)){
            return true;
        }

        String errorMessage = "Application is not of type Docker";
        log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
        return false;
    }

    public boolean downloadApp(String chainTaskId, TaskDescription taskDescription) {
        boolean isValidAppType = isValidAppType(chainTaskId, taskDescription.getAppType());
        if (!isValidAppType){
            return false;
        }

        return customDockerClient.pullImage(chainTaskId, taskDescription.getAppUri());
    }

    public boolean isAppDownloaded(String imageUri) {
        return customDockerClient.isImagePulled(imageUri);
    }

    public boolean runNonTeeComputation(TaskDescription taskDescription,
                                                                   ContributionAuthorization contributionAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
        String imageUri = taskDescription.getAppUri();
        String cmd = taskDescription.getCmd();
        long maxExecutionTime = taskDescription.getMaxExecutionTime();
        String stdout = "";

        // fetch task secrets from SMS
        boolean isFetched = smsService.fetchTaskSecrets(contributionAuth);
        if (!isFetched) {
            log.warn("No secrets fetched for this task, will continue [chainTaskId:{}]:", chainTaskId);
        }

        // decrypt data
        boolean isDatasetDecryptionNeeded = dataService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = dataService.decryptDataset(chainTaskId, taskDescription.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            stdout = "Failed to decrypt dataset, URI:" + taskDescription.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        // compute
        String datasetFilename = FileHelper.getFilenameFromUri(taskDescription.getDatasetUri());
        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .imageUri(imageUri)
                .cmd(cmd.split(" "))
                .containerName(chainTaskId)
                .maxExecutionTime(maxExecutionTime)
                .env(getContainerEnvVariables(datasetFilename, taskDescription))
                .build();

        stdout = customDockerClient.runNonTeeTaskContainer(dockerExecutionConfig);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        resultService.saveResult(chainTaskId, taskDescription, stdout);

        return true;
    }

    public boolean runTeeComputation(TaskDescription taskDescription,
                                                           ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        String imageUri = taskDescription.getAppUri();
        String datasetUri = taskDescription.getDatasetUri();
        String cmd = taskDescription.getCmd();
        long maxExecutionTime = taskDescription.getMaxExecutionTime();
        String stdout = "";

        String secureSessionId = sconeTeeService.createSconeSecureSession(contributionAuth);

        if (secureSessionId.isEmpty()) {
            stdout = "Could not generate scone secure session for tee computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        ArrayList<String> sconeAppEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/app");
        ArrayList<String> sconeEncrypterEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/encryption");

        if (sconeAppEnv.isEmpty() || sconeEncrypterEnv.isEmpty()) {
            stdout = "Could not create scone docker environment";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        String datasetFilename = FileHelper.getFilenameFromUri(datasetUri);
        for(String envVar:getContainerEnvVariables(datasetFilename, taskDescription)){
            sconeAppEnv.add(envVar);
            sconeEncrypterEnv.add(envVar);
        }

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .imageUri(imageUri)
                .cmd(cmd.split(" "))
                .containerName(chainTaskId)
                .maxExecutionTime(maxExecutionTime)
                .env(sconeAppEnv)
                .build();

        // run computation
        stdout = customDockerClient.runTeeTaskContainer(dockerExecutionConfig);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        // encrypt result
        dockerExecutionConfig.setEnv(sconeEncrypterEnv);
        stdout += customDockerClient.runTeeTaskContainer(dockerExecutionConfig);
        return  true;
    }

    private List<String> getContainerEnvVariables(String datasetFilename, TaskDescription taskDescription){
        List<String> list = new ArrayList<>();
        list.add(IEXEC_DATASET_FILENAME_ENV_PROPERTY + "=" + datasetFilename);
        list.add(IEXEC_BOT_SIZE_ENV_PROPERTY + "=" + taskDescription.getBotSize());
        list.add(IEXEC_BOT_FIRST_INDEX_ENV_PROPERTY + "=" + taskDescription.getBotFirstIndex());
        list.add(IEXEC_BOT_TASK_INDEX_ENV_PROPERTY + "=" + taskDescription.getBotIndex());
        int nbFiles = taskDescription.getInputFiles() == null ? 0 : taskDescription.getInputFiles().size();
        list.add(IEXEC_NB_INPUT_FILES_ENV_PROPERTY + "=" + nbFiles);

        int inputFileIndex = 1;
        for(String inputFile:taskDescription.getInputFiles()) {
            list.add(IEXEC_INPUT_FILES_ENV_PROPERTY_PREFIX + inputFileIndex + "=" + FilenameUtils.getName(inputFile));
            inputFileIndex++;
        }

        list.add(IEXEC_INPUT_FILES_FOLDER_ENV_PROPERTY + "=" + FileHelper.SLASH_IEXEC_IN);

        return list;
    }
}