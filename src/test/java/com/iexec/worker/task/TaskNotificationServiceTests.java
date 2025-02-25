/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.notification.TaskNotification;
import com.iexec.core.notification.TaskNotificationExtra;
import com.iexec.core.notification.TaskNotificationType;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.replicate.ReplicateActionResponse;
import com.iexec.worker.sms.SmsService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.iexec.core.notification.TaskNotificationType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskNotificationServiceTests {

    private static final String CHAIN_TASK_ID = "0xfoobar";

    @Mock
    private TaskManagerService taskManagerService;
    @Mock
    private CustomCoreFeignClient customCoreFeignClient;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private WorkerpoolAuthorizationService workerpoolAuthorizationService;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private SmsService smsService;
    @InjectMocks
    private TaskNotificationService taskNotificationService;
    @Captor
    private ArgumentCaptor<ReplicateStatusUpdate> replicateStatusUpdateCaptor;

    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .finalDeadline(Instant.now().plus(10, ChronoUnit.SECONDS).toEpochMilli())
            .build();

    void mockChainCalls() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
    }

    @Test
    void shouldNotDoAnything() {
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(null)
                .build();

        taskNotificationService.onTaskNotification(currentNotification);

        verify(customCoreFeignClient, Mockito.times(0))
                .updateReplicateStatus(anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0)).publishEvent(any());
    }

    @Test
    void shouldStoreWorkerpoolAuthorizationOnly() {
        WorkerpoolAuthorization auth = WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
        TaskNotificationExtra extra = TaskNotificationExtra.builder().workerpoolAuthorization(auth).build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_CONTINUE, extra);
        taskNotificationService.onTaskNotification(currentNotification);

        verify(workerpoolAuthorizationService, Mockito.times(1))
                .putWorkerpoolAuthorization(any());
        verify(smsService, times(0))
                .attachSmsUrlToTask(anyString(), anyString());
    }

    @Test
    void shouldStoreWorkerpoolAuthorizationAndSmsUrlIfPresent() {
        String smsUrl = "smsUrl";
        WorkerpoolAuthorization auth = WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
        TaskNotificationExtra extra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(auth)
                .smsUrl(smsUrl)
                .build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_CONTINUE, extra);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(workerpoolAuthorizationService.putWorkerpoolAuthorization(auth))
                .thenReturn(true);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(workerpoolAuthorizationService, Mockito.times(1))
                .putWorkerpoolAuthorization(any());
        verify(smsService, times(1))
                .attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
    }


    @Test
    void shouldNotStoreSmsUrlIfWorkerpoolAuthorizationIsMissing() {
        String smsUrl = "smsUrl";
        WorkerpoolAuthorization auth = WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
        TaskNotificationExtra extra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(auth)
                .smsUrl(smsUrl)
                .build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_CONTINUE, extra);
        when(workerpoolAuthorizationService.putWorkerpoolAuthorization(auth))
                .thenReturn(false);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(smsService, times(0))
                .attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
    }

    @Test
    void shouldStart() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_START);
        when(taskManagerService.start(taskDescription)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // STARTED
                .thenReturn(PLEASE_DOWNLOAD_APP);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).start(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_DOWNLOAD_APP);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToStart() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_START);
        when(taskManagerService.start(taskDescription)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).start(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldDownloadApp() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_DOWNLOAD_APP);
        when(taskManagerService.downloadApp(taskDescription)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // APP_DOWNLOADED
                .thenReturn(PLEASE_DOWNLOAD_DATA);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).downloadApp(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_DOWNLOAD_DATA);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToDownloadApp() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_DOWNLOAD_APP);
        when(taskManagerService.downloadApp(taskDescription)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).downloadApp(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldDownloadData() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_DOWNLOAD_DATA);
        when(taskManagerService.downloadData(taskDescription))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // DATA_DOWNLOADED
                .thenReturn(PLEASE_COMPUTE);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).downloadData(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_COMPUTE);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToDownloadData() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_DOWNLOAD_DATA);
        when(taskManagerService.downloadData(taskDescription)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).downloadData(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldCompute() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_COMPUTE);
        when(taskManagerService.compute(taskDescription)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // COMPUTED
                .thenReturn(PLEASE_CONTINUE);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).compute(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_CONTINUE);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToCompute() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_COMPUTE);
        when(taskManagerService.compute(taskDescription)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).compute(taskDescription);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldContribute() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_CONTRIBUTE);
        when(taskManagerService.contribute(CHAIN_TASK_ID))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // CONTRIBUTED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).contribute(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_WAIT);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToContribute() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_CONTRIBUTE);
        when(taskManagerService.contribute(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).contribute(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldContributeAndFinalize() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_CONTRIBUTE_AND_FINALIZE);
        when(taskManagerService.contributeAndFinalize(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);
        verify(taskManagerService).contributeAndFinalize(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_WAIT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldFailToContributeAndFinalize() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_CONTRIBUTE_AND_FINALIZE);
        when(taskManagerService.contributeAndFinalize(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);
        verify(taskManagerService).contributeAndFinalize(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldReveal() {
        mockChainCalls();
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(10).build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_REVEAL, extra);
        when(taskManagerService.reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra()))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // REVEALED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra());
        TaskNotification nextNotification = getTaskNotification(PLEASE_WAIT);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToReveal() {
        mockChainCalls();
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(10).build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_REVEAL, extra);
        when(taskManagerService.reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra()))
                .thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra());
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldUpload() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_UPLOAD);
        when(taskManagerService.uploadResult(CHAIN_TASK_ID))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // RESULT_UPLOADED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).uploadResult(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_WAIT);
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    void shouldFailToUpload() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_UPLOAD);
        when(taskManagerService.uploadResult(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).uploadResult(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldComplete() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_COMPLETE);
        when(taskManagerService.complete(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // COMPLETED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).complete(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_WAIT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldFailToComplete() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_COMPLETE);
        when(taskManagerService.complete(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.failure());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())).thenReturn(PLEASE_ABORT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).complete(CHAIN_TASK_ID);
        TaskNotification nextNotification = getTaskNotification(PLEASE_ABORT);
        verify(applicationEventPublisher).publishEvent(nextNotification);
    }

    @Test
    void shouldAbort() {
        mockChainCalls();
        TaskNotificationExtra extra = TaskNotificationExtra.builder().taskAbortCause(TaskAbortCause.CONTRIBUTION_TIMEOUT).build();
        TaskNotification currentNotification = getTaskNotificationWithExtra(PLEASE_ABORT, extra);
        when(taskManagerService.abort(CHAIN_TASK_ID)).thenReturn(true);
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // ABORTED
                .thenReturn(PLEASE_CONTINUE);

        taskNotificationService.onTaskNotification(currentNotification);
        verify(taskManagerService).abort(CHAIN_TASK_ID);
        verify(customCoreFeignClient).updateReplicateStatus(anyString(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldRetryCompleteUntilAchieved() {
        mockChainCalls();
        TaskNotification currentNotification = getTaskNotification(PLEASE_COMPLETE);
        when(taskManagerService.complete(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // COMPLETED
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(customCoreFeignClient, Mockito.times(4)).updateReplicateStatus(anyString(), any());
        verify(taskManagerService, Mockito.times(1)).complete(CHAIN_TASK_ID);
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    // region isFinalDeadlineReached
    @Test
    void shouldFinalDeadlineBeReached() {
        final TaskDescription expiredTaskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .finalDeadline(Instant.now().minus(1, ChronoUnit.SECONDS).toEpochMilli())
                .build();

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(expiredTaskDescription);

        final boolean finalDeadlineReached = taskNotificationService.isFinalDeadlineReached(CHAIN_TASK_ID);
        assertTrue(finalDeadlineReached);
    }

    @Test
    void shouldFinalDeadlineNotBeReached() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        final boolean finalDeadlineReached = taskNotificationService.isFinalDeadlineReached(CHAIN_TASK_ID);
        assertFalse(finalDeadlineReached);
    }
    // endregion

    // region STOMP not ready
    @Test
    void shouldAbortUpdateStatusWhenInterruptedThread() throws InterruptedException {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);

        doThrow(InterruptedException.class).when(subscriptionService).waitForSessionReady();

        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPUTING)
                .date(new Date())
                .build();
        final TaskNotificationType notification = taskNotificationService.updateStatusAndGetNextAction(CHAIN_TASK_ID, statusUpdate);

        assertNull(notification);
    }

    @Test
    void shouldResumeUpdateWhenStompReady() throws InterruptedException {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPUTING)
                .date(new Date())
                .build();

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate))
                .thenReturn(PLEASE_CONTINUE);

        final AtomicBoolean stompReady = new AtomicBoolean(false);
        doAnswer(invocation -> {
            // Wait until fake signal is emitted
            Awaitility.waitAtMost(1, TimeUnit.SECONDS)
                    .untilTrue(stompReady);
            return null;
        }).when(subscriptionService).waitForSessionReady();

        final CompletableFuture<TaskNotificationType> future = CompletableFuture.supplyAsync(
                () -> taskNotificationService.updateStatusAndGetNextAction(CHAIN_TASK_ID, statusUpdate));

        // Still waiting for the update to be sent
        assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));

        // Stomp is now ready, update can resume
        stompReady.set(true);

        // Update should have completed
        final TaskNotificationType notification = assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS));
        assertEquals(PLEASE_CONTINUE, notification);
    }
    // endregion

    private ChainTask getChainTask() {
        return ChainTask
                .builder()
                .chainTaskId(CHAIN_TASK_ID)
                .finalDeadline(Instant.now().toEpochMilli() + 100_000)  // 100 seconds from now
                .build();
    }

    private TaskNotification getTaskNotificationWithExtra(TaskNotificationType notificationType, TaskNotificationExtra notificationExtra) {
        return TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(notificationType)
                .taskNotificationExtra(notificationExtra)
                .build();
    }

    private TaskNotification getTaskNotification(TaskNotificationType notificationType) {
        return getTaskNotificationWithExtra(notificationType, null);
    }
}

