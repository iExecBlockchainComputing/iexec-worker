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
import com.iexec.worker.TestUtils;
import com.iexec.worker.TestUtils.ThreadNameWrapper;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.iexec.common.notification.TaskNotificationType.PLEASE_START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Slf4j
class ReplicateDemandServiceTests {

    private final static String ASK_FOR_REPLICATE_THREAD_NAME = "ask-for-rep-1";
    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static long BLOCK_NUMBER = 5;

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
    private Lock askForReplicateLock = new ReentrantLock();

    @Spy
    @InjectMocks
    private ReplicateDemandService replicateDemandService;

    @BeforeEach
    void init(TestInfo testInfo) {
        MockitoAnnotations.openMocks(this);
        log.info("--------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        log.info("Testing {}", testInfo.getDisplayName());
    }

    // region triggerAskForReplicate()
    @Test
    void shouldRunAskForReplicateAsynchronouslyWhenTriggeredOneTime() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(threadNameWrapper, invocation))
                .when(replicateDemandService).askForReplicate();

        replicateDemandService.triggerAskForReplicate();
        // Make sure askForReplicate method is called 1 time
        verify(replicateDemandService, timeout(1000)).askForReplicate();
        // Make sure getLatestBlockNumber method is called 1 time
        verify(iexecHubService).getLatestBlockNumber();
        assertThat(threadNameWrapper.value)
                .isEqualTo(ASK_FOR_REPLICATE_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    @Test
    void shouldRunAskForReplicateTwoConsecutiveTimesWhenTriggeredTwoConsecutiveTimes() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethod(
                threadNameWrapper, invocation))
                .when(replicateDemandService).askForReplicate();

        // Trigger 1st time
        replicateDemandService.triggerAskForReplicate();
        // Make sure askForReplicate method is called 1st time
        verify(replicateDemandService, timeout(1000)).askForReplicate();
        // Make sure getLatestBlockNumber method is called 1st time
        verify(iexecHubService).getLatestBlockNumber();
        assertThat(threadNameWrapper.value)
                .isEqualTo(ASK_FOR_REPLICATE_THREAD_NAME)
                .isNotEqualTo(mainThreadName);

        // Trigger 2nd time
        threadNameWrapper.value = "";
        replicateDemandService.triggerAskForReplicate();
        // Make sure askForReplicate method is called 2nd time
        verify(replicateDemandService, timeout(1000).times(2)).askForReplicate();
        // Make sure getLatestBlockNumber method is called 2nd time
        verify(iexecHubService, times(2)).getLatestBlockNumber();
        assertThat(threadNameWrapper.value)
                .isEqualTo(ASK_FOR_REPLICATE_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }

    /**
     * This test makes sure that the queue of the executor which runs "askForReplicate"
     * method is of size 1 and that it drops excessive incoming requests when an existing
     * request is already in the queue.
     * As you will notice, in the test we check that the method was called 2 times not
     * 1 time. That's because the queue is instantly emptied the first time so the queue
     * can accept the second request. So 2 is the least we can have.
     */
    @Test
    void shouldDropThirdAndForthAskForReplicateRequestsWhenTriggeredMultipleTimes() {
        ThreadNameWrapper threadNameWrapper = new ThreadNameWrapper();
        String mainThreadName = Thread.currentThread().getName();
        doAnswer(invocation -> TestUtils.saveThreadNameThenCallRealMethodThenSleepSomeMillis(
                threadNameWrapper, invocation, 10))
                .when(replicateDemandService).askForReplicate();

        // Trigger 4 times
        replicateDemandService.triggerAskForReplicate();
        replicateDemandService.triggerAskForReplicate();
        replicateDemandService.triggerAskForReplicate();
        replicateDemandService.triggerAskForReplicate();
        // Make sure askForReplicate method is called only 2 times
        verify(replicateDemandService, after(1000).times(2)).askForReplicate();
        // Make sure getLatestBlockNumber method is called only 2 times
        verify(iexecHubService, times(2)).getLatestBlockNumber();
        assertThat(threadNameWrapper.value)
                .isEqualTo(ASK_FOR_REPLICATE_THREAD_NAME)
                .isNotEqualTo(mainThreadName);
    }
    // endregion

    // region askForReplicate()
    @Test
    void shouldAskForReplicate() {
        WorkerpoolAuthorization workerpoolAuthorization = getStubAuth();
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicate(BLOCK_NUMBER))
                .thenReturn(Optional.of(workerpoolAuthorization));
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
                .getWorkerpoolAuthorization()).isEqualTo(workerpoolAuthorization);
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
        when(coreFeignClient.getAvailableReplicate(BLOCK_NUMBER))
                .thenReturn(Optional.empty());

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void shouldNotAskForReplicateSinceTaskIsNotInitialized() {
        WorkerpoolAuthorization workerpoolAuthorization = getStubAuth();
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicate(BLOCK_NUMBER))
                .thenReturn(Optional.of(workerpoolAuthorization));
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(false);

        replicateDemandService.askForReplicate();

        verify(subscriptionService, never()).subscribeToTopic(anyString());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    /**
     * In this test the thread that runs "{@link ReplicateDemandService#startTask(WorkerpoolAuthorization)}"
     * method will wait for second thread to be started and lock tried to be acquired before releasing the lock.
     * The second call to the method "askForReplicate" should then not be executed as the lock couldn't be acquired.
     */
    @RepeatedTest(100)
    void shouldRunAskForReplicateOnlyOnceWhenTriggeredTwoTimesSimultaneously() {
        WorkerpoolAuthorization workerpoolAuthorization = getStubAuth();
        when(iexecHubService.getLatestBlockNumber()).thenReturn(BLOCK_NUMBER);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(coreFeignClient.getAvailableReplicate(BLOCK_NUMBER))
                .thenReturn(Optional.of(workerpoolAuthorization));
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);

        doAnswer((invocation) -> {
            Awaitility
                    .waitAtMost(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> verify(askForReplicateLock, times(2)).tryLock());
            return invocation.callRealMethod();
        }).when(askForReplicateLock).unlock();

        // Trigger 2 times
        CompletableFuture.runAsync(replicateDemandService::askForReplicate);
        CompletableFuture.runAsync(replicateDemandService::askForReplicate);

        // Make sure askForReplicate method is called 1 time
        verify(replicateDemandService, after(1000)).startTask(any());
        // Make sure getLatestBlockNumber method is called 1 time
        verify(iexecHubService).getLatestBlockNumber();
    }

    private WorkerpoolAuthorization getStubAuth() {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
    }
    // endregion

}
