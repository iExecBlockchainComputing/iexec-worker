package com.iexec.worker.executor;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOADED;
import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_FAILED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TaskExecutorServiceTests {

    @Mock private DatasetService datasetService;
    @Mock private DockerComputationService dockerComputationService;
    @Mock private ResultService resultService;
    @Mock private ContributionService contributionService;
    @Mock private CustomFeignClient customFeignClient;
    @Mock private ResultRepoClient resultRepoClient;
    @Mock private RevealService revealService;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private IexecHubService iexecHubService;
    @Mock private SmsService smsService;
    

    @InjectMocks
    private TaskExecutorService taskExecutorService;

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String TEE_ENCLAVE_CHALLENGE = "enclaveChallenge";
    private static final String NO_TEE_ENCLAVE_CHALLENGE = BytesUtils.EMPTY_ADDRESS;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotComputeWhenTaskNotInitializedOnchain() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldNotComputeWhenTeeRequiredButNotSupported() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubReplicateModel(TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeTaskWhithNoTeeRequired() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeTeeTask() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(true);

        CompletableFuture<Boolean> future =
                taskExecutorService.addReplicate(getStubReplicateModel(TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeWithoutEncryptingDataset() {
        AvailableReplicateModel modelStub = getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE);

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(dockerComputationService.dockerPull(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, never()).decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(dockerComputationService, Mockito.times(1))
                .dockerRunAndGetLogs(any(), any());
    }

    @Test
    public void shouldEncryptDatasetAndCompute() {
        AvailableReplicateModel modelStub = getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE);

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(dockerComputationService.dockerPull(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(dockerComputationService, Mockito.times(1))
                .dockerRunAndGetLogs(any(), any());
    }

    @Test
    public void shouldNotComputeSinceCouldnotDecryptDataset() {
        AvailableReplicateModel modelStub = getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE);

        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(dockerComputationService.dockerPull(CHAIN_TASK_ID, modelStub.getAppUri())).thenReturn(true);
        when(datasetService.downloadDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(true);
        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(datasetService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(datasetService.decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri())).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(modelStub);
        future.join();

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);

        Mockito.verify(datasetService, Mockito.times(1))
                .decryptDataset(CHAIN_TASK_ID, modelStub.getDatasetUri());

        Mockito.verify(dockerComputationService, Mockito.times(0))
                .dockerRunAndGetLogs(any(), any());
    }

    AvailableReplicateModel getStubReplicateModel(String enclaveChallenge) {
        return AvailableReplicateModel.builder()
                .contributionAuthorization(getStubAuth(enclaveChallenge))
                .appType(DappType.DOCKER)
                .appUri("appUri")
                .datasetUri("datasetUri")
                .build();
    }

    ContributionAuthorization getStubAuth(String enclaveChallenge) {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
    }

    @Test
    public void shouldNotEncryptResult() {
        ReplicateDetails details = ReplicateDetails.builder()
        .resultLink("resultUri")
        .chainCallbackData("calbackData")
        .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);
        when(resultService.uploadResult(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(CHAIN_TASK_ID)).thenReturn(details.getChainCallbackData());

        taskExecutorService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldEncryptResult() {
        ReplicateDetails details = ReplicateDetails.builder()
        .resultLink("resultUri")
        .chainCallbackData("calbackData")
        .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.uploadResult(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(CHAIN_TASK_ID)).thenReturn(details.getChainCallbackData());

        taskExecutorService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, Mockito.times(1)).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotUploadResultSinceNotEncryptedWhenNeeded() {
        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(false);

        taskExecutorService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).uploadResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldUpdateReplicateAfterUploadResult() {
        String chainTaskId = "chainTaskId";
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("calbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(chainTaskId)).thenReturn(false);
        when(resultService.uploadResult(chainTaskId)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(chainTaskId)).thenReturn(details.getChainCallbackData());

        taskExecutorService.uploadResult(chainTaskId);

        Mockito.verify(customFeignClient, Mockito.times(0))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOADED, details);
    }

    @Test
    public void shouldNotUpdateReplicateAfterUploadResultSinceEmptyUri() {
        String chainTaskId = "chainTaskId";
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("")
                .chainCallbackData("calbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(chainTaskId)).thenReturn(false);
        when(resultService.uploadResult(chainTaskId)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(chainTaskId)).thenReturn(details.getChainCallbackData());

        taskExecutorService.uploadResult(chainTaskId);

        Mockito.verify(customFeignClient, Mockito.times(1))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
        Mockito.verify(customFeignClient, Mockito.times(0))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOADED, details);
    }
}