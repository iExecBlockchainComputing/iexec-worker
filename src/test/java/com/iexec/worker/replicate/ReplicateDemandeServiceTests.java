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

package com.iexec.worker.replicate;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


public class ReplicateDemandeServiceTests {

    private final static String CHAIN_TASK_ID = "0xfoobar";
    @InjectMocks
    ReplicateRecoveryService replicateRecoveryService;
    final long blockNumber = 5;
    @Mock
    private CustomCoreFeignClient customCoreFeignClient;
    @Mock
    private ResultService resultService;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private ComputeManagerService computeManagerService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotRecoverSinceNothingToRecover() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        when(customCoreFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.emptyList());

        List<String> recovered =
                replicateRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();
    }

    @Test
    public void shouldNotRecoverSinceCannotGetTaskDescriptionFromChain() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        TaskNotification notif =
                getStubInterruptedTask(TaskNotificationType.PLEASE_REVEAL);
        when(customCoreFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.singletonList(notif));
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(resultService.isResultAvailable(CHAIN_TASK_ID)).thenReturn(true);

        List<String> recovered =
                replicateRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(subscriptionService, Mockito.times(0))
                .subscribeToTopic(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRecoverByRevealingWhenResultNotFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        TaskNotification notif =
                getStubInterruptedTask(TaskNotificationType.PLEASE_REVEAL);
        when(customCoreFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.singletonList(notif));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);

        List<String> recovered =
                replicateRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(subscriptionService, Mockito.times(0))
                .subscribeToTopic(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRecoverByUploadingWhenResultNotFound() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        TaskNotification notif =
                getStubInterruptedTask(TaskNotificationType.PLEASE_UPLOAD);
        when(customCoreFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.singletonList(notif));
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.isResultFolderFound(CHAIN_TASK_ID)).thenReturn(false);

        List<String> recovered =
                replicateRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isEmpty();

        Mockito.verify(subscriptionService, Mockito.times(0))
                .subscribeToTopic(CHAIN_TASK_ID);
    }

    // The notification type does not matter here since it is handled on the
    // subscription service
    @Test
    public void shouldNotificationPassedToSubscriptionService() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(blockNumber);
        TaskNotification notif =
                getStubInterruptedTask(TaskNotificationType.PLEASE_COMPLETE);
        when(customCoreFeignClient.getMissedTaskNotifications(blockNumber))
                .thenReturn(Collections.singletonList(notif));
        when(resultService.isResultAvailable(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(any())).thenReturn(getStubModel());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(ComputedFile.builder().build());

        List<String> recovered =
                replicateRecoveryService.recoverInterruptedReplicates();

        assertThat(recovered).isNotEmpty();
        assertThat(recovered.get(0)).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(subscriptionService, Mockito.times(1))
                .subscribeToTopic(CHAIN_TASK_ID);
    }

    private TaskNotification getStubInterruptedTask(TaskNotificationType notificationType) {
        return TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .taskNotificationType(notificationType)
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .workerpoolAuthorization(getStubAuth())
                        .build())
                .build();

    }

    private WorkerpoolAuthorization getStubAuth() {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
    }

    private Optional<TaskDescription> getStubModel() {
        return Optional.of(TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build());
    }
}