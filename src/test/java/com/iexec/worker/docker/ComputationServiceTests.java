package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOADED;
import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_FAILED;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class ComputationServiceTests {

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String TEE_ENCLAVE_CHALLENGE = "enclaveChallenge";
    private static final String NO_TEE_ENCLAVE_CHALLENGE = BytesUtils.EMPTY_ADDRESS;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    TaskDescription getStubTaskDescription(boolean isTeeTask) {
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri("appUri")
                .datasetUri("datasetUri")
                .isTeeTask(isTeeTask)
                .build();
    }

    ContributionAuthorization getStubAuth(String enclaveChallenge) {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
    }

    
    @Test
    public void shouldComputeWithoutDecryptingDataset() {
        TaskDescription task = getStubTaskDescription(false);

        // when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        // when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
        //         .thenReturn(Optional.of(getStubTaskDescription()));
        // when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        // when(taskExecutorHelperService.checkAppType(any(), any())).thenReturn("");
        // when(taskExecutorHelperService.tryToDownloadApp(any())).thenReturn("");
        // when(taskExecutorHelperService.tryToDownloadData(any(), any())).thenReturn("");
        // when(taskExecutorHelperService.checkContributionAbility(any())).thenReturn("");
        

        when(computationService.downloadApp(CHAIN_TASK_ID, task.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(TEE_ENCLAVE_CHALLENGE));
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, never()).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(1))
                .runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldEncryptDatasetAndCompute() {
        TaskDescription modelStub = getStubTaskDescription();

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(computationService.downloadApp(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription()));

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE), false);
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(1))
                .runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldNotComputeSinceCouldnotDecryptDataset() {
        TaskDescription modelStub = getStubTaskDescription();

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(computationService.downloadApp(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(false);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription()));

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE), false);
        // CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(computationService, Mockito.times(0))
                .runNonTeeComputation(any(), any());
    }

}