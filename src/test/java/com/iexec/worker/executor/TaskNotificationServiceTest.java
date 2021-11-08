/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.executor;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import static com.iexec.common.notification.TaskNotificationType.*;
import static org.mockito.Mockito.*;

public class TaskNotificationServiceTest {

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
    private ContributionService contributionService;
    @Mock
    private IexecHubService iexecHubService;
    @InjectMocks
    private TaskNotificationService taskNotificationService;
    @Captor
    private ArgumentCaptor<ReplicateStatusUpdate> replicateStatusUpdateCaptor;
    private TaskDescription taskDescription;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        taskDescription = mock(TaskDescription.class);
    }

    @Test
    public void shouldNotDoAnything() {
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(null)
                .build();

        taskNotificationService.onTaskNotification(currentNotification);

        verify(customCoreFeignClient, Mockito.times(0))
                .updateReplicateStatus(anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0)).publishEvent(any());
    }

    @Test
    public void shouldStoreWorkerpoolAuthorizationIfPresent() {
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_CONTINUE)
                .taskNotificationExtra(TaskNotificationExtra.builder().workerpoolAuthorization(new WorkerpoolAuthorization()).build())
                .build();

        taskNotificationService.onTaskNotification(currentNotification);

        verify(contributionService, Mockito.times(1))
                .putWorkerpoolAuthorization(any());
    }

    @Test
    public void shouldAbortSinceNoTaskDescription() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(null);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_START)
                .build();
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // ABORTED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService).abort(CHAIN_TASK_ID);
        verify(customCoreFeignClient).updateReplicateStatus(anyString(), replicateStatusUpdateCaptor.capture());
        ReplicateStatusUpdate replicateStatusUpdate = replicateStatusUpdateCaptor.getValue();
        Assertions.assertEquals(ReplicateStatus.ABORTED, replicateStatusUpdate.getStatus());
        Assertions.assertEquals(ReplicateStatusCause.TASK_DESCRIPTION_NOT_FOUND, replicateStatusUpdate.getDetails().getCause());
    }

    @Test
    public void shouldStart() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_START)
                .build();
        when(taskManagerService.start(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // STARTED
                .thenReturn(PLEASE_DOWNLOAD_APP);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).start(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_DOWNLOAD_APP)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldDownloadApp() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_DOWNLOAD_APP)
                .build();
        when(taskManagerService.downloadApp(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // APP_DOWNLOADED
                .thenReturn(PLEASE_DOWNLOAD_DATA);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).downloadApp(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_DOWNLOAD_DATA)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldDownloadData() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_DOWNLOAD_DATA)
                .build();
        TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(taskManagerService.downloadData(taskDescription))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // DATA_DOWNLOADED
                .thenReturn(PLEASE_COMPUTE);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).downloadData(taskDescription);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_COMPUTE)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldCompute() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_COMPUTE)
                .build();
        when(taskManagerService.compute(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // COMPUTED
                .thenReturn(PLEASE_CONTINUE);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).compute(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_CONTINUE)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldContribute() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_CONTRIBUTE)
                .build();
        when(taskManagerService.contribute(CHAIN_TASK_ID))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // CONTRIBUTED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).contribute(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_WAIT)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldReveal() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_REVEAL)
                .taskNotificationExtra(TaskNotificationExtra.builder().blockNumber(10).build())
                .build();
        when(taskManagerService.reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra()))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // REVEALED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).reveal(CHAIN_TASK_ID, currentNotification.getTaskNotificationExtra());
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_WAIT)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldUpload() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_UPLOAD)
                .taskNotificationExtra(TaskNotificationExtra.builder().blockNumber(10).build())
                .build();

        when(taskManagerService.uploadResult(CHAIN_TASK_ID))
                .thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // RESULT_UPLOADED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).uploadResult(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_WAIT)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldComplete() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_COMPLETE)
                .build();
        when(taskManagerService.complete(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // COMPLETED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).complete(CHAIN_TASK_ID);
        verify(subscriptionService, Mockito.times(1)).unsubscribeFromTopic(any());
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldAbort() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        TaskNotification currentNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .taskAbortCause(TaskAbortCause.CONTRIBUTION_TIMEOUT)
                        .build())
                .build();
        when(taskManagerService.abort(CHAIN_TASK_ID)).thenReturn(true);
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // ABORTED
                .thenReturn(PLEASE_CONTINUE);

        taskNotificationService.onTaskNotification(currentNotification);
        verify(taskManagerService).abort(CHAIN_TASK_ID);
        verify(customCoreFeignClient).updateReplicateStatus(anyString(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }
}

