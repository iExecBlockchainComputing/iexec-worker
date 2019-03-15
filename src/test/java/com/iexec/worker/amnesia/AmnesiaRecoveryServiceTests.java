package com.iexec.worker.amnesia;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.replicate.ReplicateService;
import com.iexec.worker.result.ResultService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


public class AmnesiaRecoveryServiceTests {

    @Mock private CustomFeignClient customFeignClient;
    @Mock private SubscriptionService subscriptionService;
    @Mock private ReplicateService replicateService;
    @Mock private ResultService resultService;
    @Mock private TaskExecutorService taskExecutorService;
    @Mock private IexecHubService iexecHubService;

    @InjectMocks
    AmnesiaRecoveryService amnesiaRecoveryService;

    private final static String CHAIN_TASK_ID = "0xfoobar";
    long blockNumber = 5;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotRecoverSinceNothingToRecover() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(Collections.emptyList());

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();
    }

    @Test
    public void shouldRecoverByWaiting() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.WAIT));
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    public void shouldRecoverByComputingAgainWhenResultNotFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.CONTRIBUTE));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .addReplicate(getStubAuth(), getStubModel().get());
    }

    @Test
    public void shouldRecoverByContributingWhenResultFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.CONTRIBUTE));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(true);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .contribute(getStubAuth());
    }

    @Test
    public void shouldAbortSinceConsensusReached() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.ABORT_CONSENSUS_REACHED));
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .abortConsensusReached(CHAIN_TASK_ID);
    }

    @Test
    public void shouldAbortSinceContributionTimeout() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(
                        RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT));
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .abortContributionTimeout(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRecoverByRevealingWhenResultNotFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.REVEAL));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(taskExecutorService, Mockito.times(0))
                .reveal(CHAIN_TASK_ID);
    }

    @Test
    public void shouldRecoverByRevealingWhenResultFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.REVEAL));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(true);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .reveal(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRecoverByUploadingWhenResultNotFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.UPLOAD_RESULT));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(taskExecutorService, Mockito.times(0))
                .uploadResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldRecoverByUploadingWhenResultFound() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.UPLOAD_RESULT));
        when(replicateService.retrieveAvailableReplicateModelFromContribAuth(any()))
                .thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(true);
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .uploadResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldCompleteTask() {
        when(iexecHubService.getLastBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getInterruptedReplicates(blockNumber))
                .thenReturn(getStubInterruptedReplicateList(RecoveryAction.COMPLETE));
        
        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .completeTask(CHAIN_TASK_ID);
    }

    List<InterruptedReplicateModel> getStubInterruptedReplicateList(RecoveryAction action) {
        InterruptedReplicateModel interruptedReplicate = InterruptedReplicateModel.builder()
                .contributionAuthorization(getStubAuth())
                .recoveryAction(action)
                .build();

        return Arrays.asList(interruptedReplicate);
    }

    ContributionAuthorization getStubAuth() {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
    }

    Optional<AvailableReplicateModel> getStubModel() {
        return Optional.of(new AvailableReplicateModel());
    }

}