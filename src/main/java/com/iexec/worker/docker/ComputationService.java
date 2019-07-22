package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.messages.ContainerConfig;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.COMPUTE_FAILED;


@Slf4j
@Service
public class ComputationService {

    private static final String DATASET_FILENAME = "DATASET_FILENAME";

    private SmsService smsService;
    private DatasetService datasetService;
    private CustomDockerClient customDockerClient;
    private SconeTeeService sconeTeeService;
    private ResultService resultService;
    //HashMap<String, ReplicateStatus> computed = new HashMap<>();

    public ComputationService(SmsService smsService,
                              DatasetService datasetService,
                              CustomDockerClient customDockerClient,
                              SconeTeeService sconeTeeService,
                              ResultService resultService) {

        this.smsService = smsService;
        this.datasetService = datasetService;
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
        boolean isDatasetDecryptionNeeded = datasetService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = datasetService.decryptDataset(chainTaskId, taskDescription.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            stdout = "Failed to decrypt dataset, URI:" + taskDescription.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            //computed.putIfAbsent(chainTaskId, COMPUTE_FAILED);
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        // compute
        String datasetFilename = FileHelper.getFilenameFromUri(taskDescription.getDatasetUri());
        List<String> env = Arrays.asList(DATASET_FILENAME + "=" + datasetFilename);

        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(chainTaskId, imageUri, env, cmd);
        stdout = customDockerClient.dockerRun(chainTaskId, containerConfig, maxExecutionTime);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        resultService.saveResult(chainTaskId, taskDescription, stdout);

        //retu ReplicateStatusCause, String ?
        //return Pair.of(COMPUTED, stdout);
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
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        ArrayList<String> sconeAppEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/app");
        ArrayList<String> sconeEncrypterEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/encryption");

        if (sconeAppEnv.isEmpty() || sconeEncrypterEnv.isEmpty()) {
            stdout = "Could not create scone docker environment";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        String datasetFilename = FileHelper.getFilenameFromUri(datasetUri);
        String datasetEnv = DATASET_FILENAME + "=" + datasetFilename;
        sconeAppEnv.add(datasetEnv);
        sconeEncrypterEnv.add(datasetEnv);

        ContainerConfig sconeAppConfig = customDockerClient.buildSconeContainerConfig(chainTaskId, imageUri, sconeAppEnv, cmd);
        ContainerConfig sconeEncrypterConfig = customDockerClient.buildSconeContainerConfig(chainTaskId, imageUri, sconeEncrypterEnv, cmd);

        if (sconeAppConfig == null || sconeEncrypterConfig == null) {
            stdout = "Could not build scone container config";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        // run computation
        stdout = customDockerClient.dockerRun(chainTaskId, sconeAppConfig, maxExecutionTime);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            //return Pair.of(COMPUTE_FAILED, stdout);
            return false;
        }

        // encrypt result
        stdout += customDockerClient.dockerRun(chainTaskId, sconeEncrypterConfig, maxExecutionTime);
        //return Pair.of(COMPUTED, stdout);
        return  true;
    }
}