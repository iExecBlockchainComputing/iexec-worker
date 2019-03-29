package com.iexec.worker.executor;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
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

    String CHAIN_TASK_ID = "0xfoobar";
    String ENCLAVE_CHALLENGE = "enclaveChallenge";

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotComputeWhenTaskNotInitializedOnchain() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(false);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel());
        future.join();

        Mockito.verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldComputeWhenTaskIsInitializedOnchain() throws InterruptedException, ExecutionException {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(publicConfigurationService.getChainId()).thenReturn(1234);

        CompletableFuture<Void> future = taskExecutorService.addReplicate(getStubReplicateModel());
        future.join();

        Mockito.verify(customFeignClient, Mockito.atLeastOnce())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }


    AvailableReplicateModel getStubReplicateModel() {
        return AvailableReplicateModel.builder()
                .contributionAuthorization(getStubAuth())
                .appType(DappType.BINARY)
                .build();
    }

    ContributionAuthorization getStubAuth() {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .build();
    }
}