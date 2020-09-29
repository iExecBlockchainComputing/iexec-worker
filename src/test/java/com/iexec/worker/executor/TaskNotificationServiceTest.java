package com.iexec.worker.executor;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import static com.iexec.common.notification.TaskNotificationType.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @InjectMocks
    private TaskNotificationService taskNotificationService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
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
    public void shouldStart() {
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
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_DOWNLOAD_DATA)
                .build();
        when(taskManagerService.downloadData(CHAIN_TASK_ID)).thenReturn(ReplicateActionResponse.success());
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // DATA_DOWNLOADED
                .thenReturn(PLEASE_COMPUTE);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).downloadData(CHAIN_TASK_ID);
        TaskNotification nextNotification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_COMPUTE)
                .build();
        verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(nextNotification);
    }

    @Test
    public void shouldCompute() {
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
        TaskNotification currentNotification = TaskNotification.builder().chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(PLEASE_ABORT_CONSENSUS_REACHED)
                .build();
        when(taskManagerService.abort(CHAIN_TASK_ID)).thenReturn(true);
        when(customCoreFeignClient.updateReplicateStatus(anyString(), any())) // ABORTED
                .thenReturn(PLEASE_WAIT);

        taskNotificationService.onTaskNotification(currentNotification);

        verify(taskManagerService, Mockito.times(1)).abort(CHAIN_TASK_ID);
        verify(subscriptionService, Mockito.times(1)).unsubscribeFromTopic(any());
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }
}

