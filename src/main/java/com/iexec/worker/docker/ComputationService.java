package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.FileHelper;
import org.apache.commons.lang3.tuple.Pair;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import static com.iexec.common.replicate.ReplicateStatus.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Slf4j
@Service
public class ComputationService {

    private static final String DATASET_FILENAME = "DATASET_FILENAME";

    private SmsService smsService;
    private DataService dataService;
    private CustomDockerClient customDockerClient;
    private SconeTeeService sconeTeeService;

    public ComputationService(SmsService smsService,
                              DataService dataService,
                              CustomDockerClient customDockerClient,
                              SconeTeeService sconeTeeService) {

        this.smsService = smsService;
        this.dataService = dataService;
        this.customDockerClient = customDockerClient;
        this.sconeTeeService = sconeTeeService;
    }

    public boolean downloadApp(String chainTaskId, String appUri) {
        return customDockerClient.pullImage(chainTaskId, appUri);
    }

    public Pair<ReplicateStatus, String> runNonTeeComputation(TaskDescription taskDescription,
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
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        // compute
        String datasetFilename = FileHelper.getFilenameFromUri(taskDescription.getDatasetUri());
        List<String> env = Arrays.asList(DATASET_FILENAME + "=" + datasetFilename);

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .imageUri(imageUri)
                .cmd(cmd.split(" "))
                .containerName(chainTaskId)
                .maxExecutionTime(maxExecutionTime)
                .env(env)
                .build();

        stdout = customDockerClient.runNonTeeTaskContainer(dockerExecutionConfig);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        return Pair.of(COMPUTED, stdout);        
    }

    public Pair<ReplicateStatus, String> runTeeComputation(TaskDescription taskDescription,
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
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        ArrayList<String> sconeAppEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/app");
        ArrayList<String> sconeEncrypterEnv = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/encryption");

        if (sconeAppEnv.isEmpty() || sconeEncrypterEnv.isEmpty()) {
            stdout = "Could not create scone docker environment";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        String datasetFilename = FileHelper.getFilenameFromUri(datasetUri);
        String datasetEnv = DATASET_FILENAME + "=" + datasetFilename;
        sconeAppEnv.add(datasetEnv);
        sconeEncrypterEnv.add(datasetEnv);

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
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        // encrypt result
        dockerExecutionConfig.setEnv(sconeEncrypterEnv);
        stdout += customDockerClient.runTeeTaskContainer(dockerExecutionConfig);
        return Pair.of(COMPUTED, stdout);
    }
}