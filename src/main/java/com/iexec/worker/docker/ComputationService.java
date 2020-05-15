package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.postcompute.PostComputeResult;
import com.iexec.worker.docker.precompute.PreComputeResult;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;

import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;


@Slf4j
@Service
public class ComputationService {

    // env variables that will be injected in the container of a task computation
    private static final String IEXEC_IN_ENV_PROPERTY = "IEXEC_IN";
    private static final String IEXEC_OUT_ENV_PROPERTY = "IEXEC_OUT";
    private static final String IEXEC_DATASET_FILENAME_ENV_PROPERTY = "IEXEC_DATASET_FILENAME";
    private static final String IEXEC_BOT_TASK_INDEX_ENV_PROPERTY = "IEXEC_BOT_TASK_INDEX";
    private static final String IEXEC_BOT_SIZE_ENV_PROPERTY = "IEXEC_BOT_SIZE";
    private static final String IEXEC_BOT_FIRST_INDEX_ENV_PROPERTY = "IEXEC_BOT_FIRST_INDEX";
    private static final String IEXEC_NB_INPUT_FILES_ENV_PROPERTY = "IEXEC_NB_INPUT_FILES";
    private static final String IEXEC_INPUT_FILES_ENV_PROPERTY_PREFIX = "IEXEC_INPUT_FILE_NAME_";
    private static final String IEXEC_INPUT_FILES_FOLDER_ENV_PROPERTY = "IEXEC_INPUT_FILES_FOLDER";

    @Value("${encryptFilePath}")
    private String scriptFilePath;

    private SmsService smsService;
    private DataService dataService;
    private CustomDockerClient customDockerClient;
    private SconeTeeService sconeTeeService;
    private WorkerConfigurationService workerConfigService;
    private PublicConfigurationService publicConfigService;

    public ComputationService(SmsService smsService,
                              DataService dataService,
                              CustomDockerClient customDockerClient,
                              SconeTeeService sconeTeeService,
                              WorkerConfigurationService workerConfigService,
                              PublicConfigurationService publicConfigService) {

        this.smsService = smsService;
        this.dataService = dataService;
        this.customDockerClient = customDockerClient;
        this.sconeTeeService = sconeTeeService;
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

    /*
     * TEE: download post-compute image && create secure session
     * non TEE: download secrets && decrypt dataset (TODO: this should be rewritten or removed)
     * 
     */
    public PreComputeResult runPreCompute(TaskDescription taskDescription, ContributionAuthorization contributionAuth) {
        if (taskDescription.isTeeTask()) {
            String secureSessionId = runTeePreCompute(taskDescription, contributionAuth);
            return !secureSessionId.isEmpty() ? PreComputeResult.success(secureSessionId) : PreComputeResult.failure();
        }

        boolean isSuccess = runNonTeePreCompute(taskDescription, contributionAuth);
        return isSuccess ? PreComputeResult.success() : PreComputeResult.failure();
    }

    private String runTeePreCompute(TaskDescription taskDescription, ContributionAuthorization contributionAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
        if (!customDockerClient.pullImage(chainTaskId, taskDescription.getTeePostComputeImage())) {
            log.error("Cannot pull TEE post compute image [chainTaskId:{}, imageUri:{}]",
                    chainTaskId, taskDescription.getTeePostComputeImage());
            return "";
        }

        String secureSessionId = smsService.createTeeSession(contributionAuth);
        if (secureSessionId.isEmpty()) {
            log.error("Cannot compute TEE task without secure session [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Secure session created [chainTaskId:{}, secureSessionId:{}]", chainTaskId, secureSessionId);
        }
        return secureSessionId;
    }

    private boolean runNonTeePreCompute(TaskDescription taskDescription, ContributionAuthorization contributionAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
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
        return true;
    }

    public ComputeResponse runComputation(TaskDescription taskDescription, PreComputeResult preComputeResult) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> env = getContainerEnvVariables(taskDescription);
        if (taskDescription.isTeeTask()) {
            List<String> teeEnv = sconeTeeService.buildSconeDockerEnv(preComputeResult.getSecureSessionId() + "/app",
                    publicConfigService.getSconeCasURL(), "1G");
            env.addAll(teeEnv);
        }
        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put(workerConfigService.getTaskInputDir(chainTaskId), FileHelper.SLASH_IEXEC_IN);
        bindPaths.put(workerConfigService.getTaskIexecOutDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT);
        DockerExecutionConfig appExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTaskContainerName(chainTaskId))
                .imageUri(taskDescription.getAppUri())
                .cmd(taskDescription.getCmd())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .bindPaths(bindPaths)
                .isSgx(taskDescription.isTeeTask())
                .build();
        DockerExecutionResult appExecutionResult = customDockerClient.execute(appExecutionConfig);
        if (shouldPrintDeveloperLogs(taskDescription)){
            log.info("Developer logs of computing stage [chainTaskId:{}, logs:{}]", chainTaskId,
                    getDockerExecutionDeveloperLogs(chainTaskId, appExecutionResult));
        }
        if (!appExecutionResult.isSuccess()) {
            log.error("Failed to compute [chainTaskId:{}]", chainTaskId);
            return ComputeResponse.failure();
        }
        //TODO: Remove logs before merge
        System.out.println("****** App");
        System.out.println(appExecutionResult.getStdout());
        return ComputeResponse.success(appExecutionResult.getStdout());
    }

    /*
     * Move files produced by the compute stage in SLASH_IEXEC_OUT
     * to SLASH_IEXEC_RESULT. Encrypt them if requested.
     */
    public PostComputeResult runPostCompute(PreComputeResult preComputeResult, TaskDescription taskDescription) {
        if (taskDescription.isTeeTask()) {
            return runTeePostCompute(preComputeResult.getSecureSessionId(), taskDescription);
        } else {
            return runNonTeePostCompute(taskDescription);
        }
    }

    private PostComputeResult runTeePostCompute(String secureSessionId, TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> sconeUploaderEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/post-compute",
                publicConfigService.getSconeCasURL(), "3G");

        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put(workerConfigService.getTaskIexecOutDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT);
        bindPaths.put(workerConfigService.getTaskResultDir(chainTaskId), FileHelper.SLASH_RESULT);

        DockerExecutionConfig teePostComputeExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTaskTeePostComputeContainerName(chainTaskId))
                .imageUri(taskDescription.getTeePostComputeImage())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(sconeUploaderEnv)
                .bindPaths(bindPaths)
                .isSgx(true)
                .build();

        DockerExecutionResult teePostComputeExecutionResult = customDockerClient.execute(teePostComputeExecutionConfig);
        if (!teePostComputeExecutionResult.isSuccess()) {
            log.error("Failed to process post-compute on result [chainTaskId:{}]", chainTaskId);
            return PostComputeResult.failure();
        }
        System.out.println("****** Tee post-compute");
        System.out.println(teePostComputeExecutionResult.getStdout());
        return PostComputeResult.success(teePostComputeExecutionResult.getStdout());
    }

    private PostComputeResult runNonTeePostCompute(TaskDescription taskDescription) {
        // iexec_out -> iexec_result
        String chainTaskId = taskDescription.getChainTaskId();
        String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);
        boolean shouldEncryptResult = !new File(beneficiarySecretFilePath).exists();
        if (!shouldEncryptResult) {
            log.info("No beneficiary secret file found, will continue without encrypting result [chainTaskId:{}]", chainTaskId);
        }
        if (shouldEncryptResult && !encryptResult(chainTaskId)) {
            log.error("Cannot upload, failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(RESULT_ENCRYPTION_FAILED);
        }
        return PostComputeResult.success("");
    }

    public boolean encryptResult(String chainTaskId) {
        String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);
        String resultZipFilePath = getResultZipFilePath(chainTaskId);
        String taskOutputDir = workerConfigService.getTaskOutputDir(chainTaskId);

        log.info("Encrypting result zip [resultZipFilePath:{}, beneficiarySecretFilePath:{}]",
                resultZipFilePath, beneficiarySecretFilePath);

        encryptFile(taskOutputDir, resultZipFilePath, beneficiarySecretFilePath);

        String encryptedResultFilePath = getEncryptedResultFilePath(chainTaskId);

        if (!new File(encryptedResultFilePath).exists()) {
            log.error("Encrypted result file not found [chainTaskId:{}, encryptedResultFilePath:{}]",
                    chainTaskId, encryptedResultFilePath);
            return false;
        }

        // replace result file with the encypted one
        return FileHelper.replaceFile(resultZipFilePath, encryptedResultFilePath);
    }

    private void encryptFile(String taskOutputDir, String resultZipFilePath, String publicKeyFilePath) {
        String options = String.format("--root-dir=%s --result-file=%s --key-file=%s",
                taskOutputDir, resultZipFilePath, publicKeyFilePath);

        String cmd = this.scriptFilePath + " " + options;

        ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));

        try {
            Process pr = pb.start();

            BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
    
            while ((line = in.readLine()) != null) { log.info(line); }
    
            pr.waitFor();
            in.close();
        } catch (Exception e) {
            log.error("Error while trying to encrypt result [resultZipFilePath{}, publicKeyFilePath:{}]",
                    resultZipFilePath, publicKeyFilePath);
            e.printStackTrace();
        }
    }

    private List<String> getContainerEnvVariables(TaskDescription taskDescription) {
        String datasetFilename = FileHelper.getFilenameFromUri(taskDescription.getDatasetUri());
        List<String> list = new ArrayList<>();
        list.add(IEXEC_IN_ENV_PROPERTY + "=" + FileHelper.SLASH_IEXEC_IN);
        list.add(IEXEC_OUT_ENV_PROPERTY + "=" + FileHelper.SLASH_IEXEC_OUT);
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

    // private Map<String, String> getDefaultBindPaths(String chainTaskId) {
    //     Map<String, String> bindPaths = new HashMap<>();
    //     bindPaths.put(workerConfigService.getTaskInputDir(chainTaskId), FileHelper.SLASH_IEXEC_IN);
    //     bindPaths.put(workerConfigService.getTaskIexecOutDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT);
    //     return bindPaths;
    // }

    // private Map<String, String> getTeeComputeBindPaths(String chainTaskId) {
    //     Map<String, String> bindPaths = getDefaultBindPaths(chainTaskId);
    //     bindPaths.put(workerConfigService.getTaskIexecOutUnprotectedDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT_UNPROTECTED);
    //     return bindPaths;
    // }

    // private Map<String, String> getTeePostComputeBindPaths(String chainTaskId) {
    //     Map<String, String> bindPaths = new HashMap<>();
    //     bindPaths.put(workerConfigService.getTaskIexecOutDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT);
    //     bindPaths.put(workerConfigService.getTaskIexecOutUnprotectedDir(chainTaskId), FileHelper.SLASH_IEXEC_OUT_UNPROTECTED);
    //     return bindPaths;
    // }

    // We use the name "worker1-0xabc123" for app container to avoid
    // conflicts when running multiple workers on the same machine.
    // Exp: integration tests
    private String getTaskContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId;
    }

    private String getTaskTeePostComputeContainerName(String chainTaskId) {
        return getTaskContainerName(chainTaskId) + "-tee-post-compute";
    }

    private boolean shouldPrintDeveloperLogs(TaskDescription taskDescription) {
        return workerConfigService.isDeveloperLoggerEnabled() && taskDescription.isDeveloperLoggerEnabled();
    }

    private String getDockerExecutionDeveloperLogs(String chainTaskId, DockerExecutionResult dockerExecutionResult) {
        String iexecInTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskInputDir(chainTaskId)));
        iexecInTree = iexecInTree.replace("├── input/", "├── iexec_in/");//confusing for developers if not replaced
        String iexecOutTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskIexecOutDir(chainTaskId)));
        String stdout = dockerExecutionResult.getStdout();

        return LoggingUtils.prettifyDeveloperLogs(iexecInTree, iexecOutTree, stdout);
    }
}