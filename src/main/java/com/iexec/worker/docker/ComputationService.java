package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.sms.secrets.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.util.*;


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
    private WorkerConfigurationService workerConfigService;
    private PublicConfigurationService publicConfigService;

    public ComputationService(SmsService smsService,
                              DataService dataService,
                              CustomDockerClient customDockerClient,
                              SconeTeeService sconeTeeService,
                              ResultService resultService,
                              WorkerConfigurationService workerConfigService,
                              PublicConfigurationService publicConfigService) {

        this.smsService = smsService;
        this.dataService = dataService;
        this.customDockerClient = customDockerClient;
        this.sconeTeeService = sconeTeeService;
        this.resultService = resultService;
        this.workerConfigService = workerConfigService;
        this.publicConfigService = publicConfigService;
    }

    public boolean isValidAppType(String chainTaskId, DappType type) {
        if (type.equals(DappType.DOCKER)) {
            return true;
        }

        String errorMessage = "Application is not of type Docker";
        log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
        return false;
    }

    public boolean downloadApp(String chainTaskId, TaskDescription taskDescription) {
        boolean isValidAppType = isValidAppType(chainTaskId, taskDescription.getAppType());
        if (!isValidAppType) {
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

        // fetch task secrets from SMS
        Optional<TaskSecrets> oTaskSecrets = smsService.fetchTaskSecrets(contributionAuth);
        if (!oTaskSecrets.isPresent()) {
            log.warn("No secrets fetched for this task, will continue [chainTaskId:{}]:", chainTaskId);
        } else {
            String datasetSecretFilePath = workerConfigService.getDatasetSecretFilePath(chainTaskId);
            String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);
            String enclaveSecretFilePath = workerConfigService.getEnclaveSecretFilePath(chainTaskId);
            smsService.saveSecrets(chainTaskId, oTaskSecrets.get(), datasetSecretFilePath,
                    beneficiarySecretFilePath, enclaveSecretFilePath);
        }

        // decrypt data
        boolean isDatasetDecryptionNeeded = dataService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = dataService.decryptDataset(chainTaskId, taskDescription.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            log.error("Failed to decrypt dataset [chainTaskId:{}, uri:{}]",
                    chainTaskId, taskDescription.getDatasetUri());
            return false;
        }

        // compute
        String datasetFilename = FileHelper.getFilenameFromUri(taskDescription.getDatasetUri());
        List<String> env = getContainerEnvVariables(datasetFilename, taskDescription);

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTaskContainerName(chainTaskId))
                .imageUri(imageUri)
                .cmd(cmd.split(" "))
                .maxExecutionTime(maxExecutionTime)
                .env(env)
                .bindPaths(getDefaultBindPaths(chainTaskId))
                .isSgx(false)
                .build();

        DockerExecutionResult appExecutionResult = customDockerClient.execute(dockerExecutionConfig);

        if (!appExecutionResult.isSuccess()) {
            log.error("Failed to compute [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return resultService.saveResult(chainTaskId, taskDescription, appExecutionResult.getStdout());
    }

    public boolean runTeeComputation(TaskDescription taskDescription,
                                     ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        String imageUri = taskDescription.getAppUri();
        String datasetUri = taskDescription.getDatasetUri();
        String cmd = taskDescription.getCmd();
        long maxExecutionTime = taskDescription.getMaxExecutionTime();


        String secureSessionId = sconeTeeService.createSconeSecureSession(contributionAuth);

        log.info("Secure session created [chainTaskId:{}, secureSessionId:{}]", chainTaskId, secureSessionId);

        if (secureSessionId.isEmpty()) {
            String msg = "Could not generate scone secure session for tee computation";
            log.error(msg + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        ArrayList<String> sconeAppEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/app",
                publicConfigService.getSconeCasURL());
        ArrayList<String> sconeEncrypterEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/encryption",
                publicConfigService.getSconeCasURL());

        if (sconeAppEnv.isEmpty() || sconeEncrypterEnv.isEmpty()) {
            log.error("Could not create scone docker environment [chainTaskId:{}]", chainTaskId);
            return false;
        }

        String datasetFilename = FileHelper.getFilenameFromUri(datasetUri);
        for (String envVar : getContainerEnvVariables(datasetFilename, taskDescription)) {
            sconeAppEnv.add(envVar);
            sconeEncrypterEnv.add(envVar);
        }

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTaskContainerName(chainTaskId))
                .imageUri(imageUri)
                .cmd(cmd.split(" "))
                .maxExecutionTime(maxExecutionTime)
                .env(sconeAppEnv)
                .bindPaths(getSconeBindPaths(chainTaskId))
                .isSgx(true)
                .build();

        // run computation
        DockerExecutionResult appExecutionResult = customDockerClient.execute(dockerExecutionConfig);
        if (!appExecutionResult.isSuccess()) {
            log.error("Failed to compute [chainTaskId:{}]", chainTaskId);
            return false;
        }

        // encrypt result
        dockerExecutionConfig.setEnv(sconeEncrypterEnv);
        DockerExecutionResult encryptionExecutionResult = customDockerClient.execute(dockerExecutionConfig);
        if (!encryptionExecutionResult.isSuccess()) {
            log.error("Failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return false;
        }

        String stdout = appExecutionResult.getStdout() + encryptionExecutionResult.getStdout();
        return resultService.saveResult(chainTaskId, taskDescription, stdout);
    }

    private List<String> getContainerEnvVariables(String datasetFilename, TaskDescription taskDescription) {
        List<String> list = new ArrayList<>();
        list.add(IEXEC_DATASET_FILENAME_ENV_PROPERTY + "=" + datasetFilename);
        list.add(IEXEC_BOT_SIZE_ENV_PROPERTY + "=" + taskDescription.getBotSize());
        list.add(IEXEC_BOT_FIRST_INDEX_ENV_PROPERTY + "=" + taskDescription.getBotFirstIndex());
        list.add(IEXEC_BOT_TASK_INDEX_ENV_PROPERTY + "=" + taskDescription.getBotIndex());
        int nbFiles = taskDescription.getInputFiles() == null ? 0 : taskDescription.getInputFiles().size();
        list.add(IEXEC_NB_INPUT_FILES_ENV_PROPERTY + "=" + nbFiles);

        int inputFileIndex = 1;
        for (String inputFile : taskDescription.getInputFiles()) {
            list.add(IEXEC_INPUT_FILES_ENV_PROPERTY_PREFIX + inputFileIndex + "=" + FilenameUtils.getName(inputFile));
            inputFileIndex++;
        }

        list.add(IEXEC_INPUT_FILES_FOLDER_ENV_PROPERTY + "=" + FileHelper.SLASH_IEXEC_IN);

        return list;
    }

    private Map<String, String> getDefaultBindPaths(String chainTaskId) {
        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put(workerConfigService.getTaskInputDir(chainTaskId), FileHelper.SLASH_IEXEC_IN);
        bindPaths.put(workerConfigService.getTaskIexecOutDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT);
        return bindPaths;
    }

    private Map<String, String> getSconeBindPaths(String chainTaskId) {
        Map<String, String> bindPaths = getDefaultBindPaths(chainTaskId);
        bindPaths.put(workerConfigService.getTaskSconeDir(chainTaskId), FileHelper.SLASH_SCONE);
        return bindPaths;
    }

    // We use the name "worker1-0xabc123" for app container to avoid
    // conflicts when running multiple workers on the same machine.
    // Exp: integration tests
    private String getTaskContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId;
    }
}