package com.iexec.worker.executor;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.sms.tee.SmsSecureSessionResponse.SmsSecureSession;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.sms.SmsService;
import com.netflix.util.Pair;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import static com.iexec.common.replicate.ReplicateStatus.*;

import java.util.Optional;


@Slf4j
@Service
public class ComputationService {

    private SmsService smsService;
    private DatasetService datasetService;
    private DockerComputationService dockerComputationService;

    public ComputationService(SmsService smsService,
                              DatasetService datasetService,
                              DockerComputationService dockerComputationService) {

        this.smsService = smsService;
        this.datasetService = datasetService;
        this.dockerComputationService = dockerComputationService;
    }


    public Pair<ReplicateStatus, String> runComputationWithTee(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();

        // generate secure session
        Optional<SmsSecureSession> oSmsSecureSession = smsService.generateTaskSecureSession(contributionAuth);
        if (!oSmsSecureSession.isPresent()) {
            String stdout = "Could not generate secure session for tee computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return new Pair<ReplicateStatus, String>(COMPUTE_FAILED, stdout);
        }

        SmsSecureSession smsSecureSession = oSmsSecureSession.get();
        log.info("smsSecureSession: {}", smsSecureSession);

        return null;
    }

    public Pair<ReplicateStatus, String> runComputationWithoutTee(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();

        // fetch task secrets from SMS
        boolean isFetched = smsService.fetchTaskSecrets(contributionAuth);
        if (!isFetched) {
            log.warn("No secrets fetched for this task, will continue [chainTaskId:{}]:", chainTaskId);
        }

        // decrypt data
        boolean isDatasetDecryptionNeeded = datasetService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = datasetService.decryptDataset(chainTaskId, replicateModel.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            String stdout = "Failed to decrypt dataset, URI:" + replicateModel.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return new Pair<ReplicateStatus,String>(COMPUTE_FAILED, stdout);
        }

        // compute
        String datasetFilename = datasetService.getDatasetFilename(replicateModel.getDatasetUri());
        String stdout = dockerComputationService.dockerRunAndGetLogs(replicateModel, datasetFilename);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return new Pair<ReplicateStatus, String>(COMPUTE_FAILED, stdout);
        }

        return new Pair<ReplicateStatus, String>(COMPUTED, stdout);        
    }
}