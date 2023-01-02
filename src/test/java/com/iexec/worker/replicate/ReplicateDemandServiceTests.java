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
import com.iexec.common.replicate.ReplicateTaskSummary;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.utils.AsyncUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static com.iexec.common.notification.TaskNotificationType.PLEASE_START;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReplicateDemandServiceTests {

    private static final String ASK_FOR_REPLICATE_CONTEXT = "ask-for-replicate";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String SMS_URL = "smsUrl";
    private static final long BLOCK_NUMBER = 5;

    @Captor
    ArgumentCaptor<TaskNotification> taskNotificationCaptor;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private CustomCoreFeignClient coreFeignClient;
    @Mock
    private ContributionService contributionService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Spy
    @InjectMocks
    private ReplicateDemandService replicateDemandService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region triggerAskForReplicate()
    @Test
    void shouldRunAskForReplicateAsynchronouslyWhenTriggeredOneTime() {
        try (MockedStatic<AsyncUtils> asyncUtils = Mockito.mockStatic(AsyncUtils.class)) {
            asyncUtils.when(() -> AsyncUtils.runAsyncTask(eq(ASK_FOR_REPLICATE_CONTEXT), any(), any()))
                    .thenReturn(null);
            replicateDemandService.triggerAskForReplicate();
            asyncUtils.verify(() -> AsyncUtils.runAsyncTask(eq(ASK_FOR_REPLICATE_CONTEXT), any(), any()), times(1));
        }
    }
    // endregion

    // region askForReplicate()
    @Test
    void shouldAskForReplicate() {
        ReplicateTaskSummary replicateTaskSummary = getStubReplicateTaskSummary();
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicateTaskSummary(BLOCK_NUMBER))
                .thenReturn(Optional.of(replicateTaskSummary));
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);

        replicateDemandService.askForReplicate();

        verify(subscriptionService).subscribeToTopic(CHAIN_TASK_ID);
        verify(applicationEventPublisher).publishEvent(taskNotificationCaptor.capture());
        Assertions.assertThat(taskNotificationCaptor.getValue().getChainTaskId())
                .isEqualTo(CHAIN_TASK_ID);
        Assertions.assertThat(taskNotificationCaptor.getValue().getWorkersAddress())
                .isEmpty();
        Assertions.assertThat(taskNotificationCaptor.getValue().getTaskNotificationType())
                .isEqualTo(PLEASE_START);
        Assertions.assertThat(taskNotificationCaptor.getValue().getTaskNotificationExtra()
                .getWorkerpoolAuthorization()).isEqualTo(replicateTaskSummary.getWorkerpoolAuthorization());
        Assertions.assertThat(taskNotificationCaptor.getValue().getTaskNotificationExtra()
                .getSmsUrl()).isEqualTo(replicateTaskSummary.getSmsUrl());
    }

    @Test
    void shouldNotAskForReplicateSinceLocalBlockchainNotSynchronized() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotAskForReplicateSinceWalletIsDry() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotAskForReplicateSinceNoAvailableReplicate() {
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicateTaskSummary(BLOCK_NUMBER))
                .thenReturn(Optional.empty());

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotAskForReplicateSinceTaskIsNotInitialized() {
        ReplicateTaskSummary replicateTaskSummary = getStubReplicateTaskSummary();
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicateTaskSummary(BLOCK_NUMBER))
                .thenReturn(Optional.of(replicateTaskSummary));
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(false);

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    private WorkerpoolAuthorization getStubAuth() {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
    }

    private ReplicateTaskSummary getStubReplicateTaskSummary() {
        return ReplicateTaskSummary.builder()
                .workerpoolAuthorization(getStubAuth())
                .smsUrl(SMS_URL)
                .build();
    }
    // endregion

}
