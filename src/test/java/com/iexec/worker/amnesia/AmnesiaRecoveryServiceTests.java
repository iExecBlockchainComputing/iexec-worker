package com.iexec.worker.amnesia;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


public class AmnesiaRecoveryServiceTests {

    @Mock
    private CustomFeignClient customFeignClient;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ResultService resultService;
    @Mock
    private TaskExecutorService taskExecutorService;
    @Mock
    private IexecHubService iexecHubService;

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
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.emptyList());

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();
    }

    @Test
    public void shouldRecoverByWaiting() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(resultService.isResultAvailable(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_WAIT));

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    public void shouldRecoverByComputingAgainWhenResultNotFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_CONTRIBUTE));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .addReplicate(any(ContributionAuthorization.class));
    }

    @Test
    public void shouldRecoverByContributingWhenResultFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_CONTRIBUTE));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultAvailable(CHAIN_TASK_ID)).thenReturn(true);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .contribute(getStubAuth());
    }

    @Test
    public void shouldAbortSinceConsensusReached() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_ABORT_CONSENSUS_REACHED));
        when(resultService.isResultAvailable(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        // when(subscriptionService.handleTaskNotification(any())).

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .abortConsensusReached(CHAIN_TASK_ID);
    }

    @Test
    public void shouldAbortSinceContributionTimeout() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_ABORT_CONTRIBUTION_TIMEOUT));
        when(resultService.isResultZipFound(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .abortContributionTimeout(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRecoverByRevealingWhenResultNotFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_REVEAL));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(taskExecutorService, Mockito.times(0))
                .reveal(CHAIN_TASK_ID, blockNumber);
    }

    @Test
    public void shouldRecoverByRevealingWhenResultFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_REVEAL));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(true);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .reveal(CHAIN_TASK_ID, blockNumber);
    }

    @Test
    public void shouldNotRecoverByUploadingWhenResultNotFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_UPLOAD));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(taskExecutorService, Mockito.times(0))
                .uploadResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldRecoverByUploadingWhenResultFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_UPLOAD));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(true);

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .uploadResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldCompleteTask() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(getStubInterruptedTasks(TaskNotificationType.PLEASE_COMPLETE));

        when(resultService.isResultZipFound(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());

        List<String> recovered = amnesiaRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(taskExecutorService, Mockito.times(1))
                .completeTask(CHAIN_TASK_ID);
    }

    List<TaskNotification> getStubInterruptedTasks(TaskNotificationType notificationType) {
        TaskNotification interruptedReplicate = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(notificationType)
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .contributionAuthorization(getStubAuth())
                        .build())
                .build();

        return Collections.singletonList(interruptedReplicate);
    }

    ContributionAuthorization getStubAuth() {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
    }

    Optional<TaskDescription> getStubModel() {
        return Optional.of(TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build());
    }

}