package com.iexec.worker.executor;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOADED;
import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_FAILED;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;

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
    @Mock private PublicConfigurationService publicConfigurationService;
    @Mock private CredentialsService credentialsService;
    @Mock private WorkerConfigurationService workerConfigurationService;

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
    public void shouldNotComputeWhenTaskNotInitializedOnchain() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(false);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldNotComputeWhenTeeRequiredButNotSupported() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(publicConfigurationService.getChainId()).thenReturn(1234);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel(TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeTaskWhithNoTeeRequired() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(publicConfigurationService.getChainId()).thenReturn(1234);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, Mockito.atLeastOnce())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeTeeTask() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(publicConfigurationService.getChainId()).thenReturn(1234);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(true);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel(TEE_ENCLAVE_CHALLENGE));
        future.join();

        Mockito.verify(customFeignClient, Mockito.atLeastOnce())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }


    AvailableReplicateModel getStubReplicateModel(String enclaveChallenge) {
        return AvailableReplicateModel.builder()
                .contributionAuthorization(getStubAuth(enclaveChallenge))
                .appType(DappType.BINARY)
                .build();
    }

    ContributionAuthorization getStubAuth(String enclaveChallenge) {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
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