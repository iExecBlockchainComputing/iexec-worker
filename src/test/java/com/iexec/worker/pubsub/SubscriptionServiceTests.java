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

package com.iexec.worker.pubsub;

import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubscriptionServiceTests {

    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private StompClientService stompClientService;

    @Spy
    @InjectMocks
    SubscriptionService subscriptionService;

    private static final String WORKER_WALLET_ADDRESS = "0x1234";
    private static final String CHAIN_TASK_ID = "chaintaskid";
    private static final String CHAIN_TASK_ID_2 = "chaintaskid2";
    private static final Optional<Subscription> SUBSCRIPTION =
            Optional.of(mock(Subscription.class));

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(workerConfigurationService.getWorkerWalletAddress())
                .thenReturn(WORKER_WALLET_ADDRESS);
    }

    @Test
    void shouldSubscribeToTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);

        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClientService, times(1)).subscribeToTopic(anyString(), any());
    }

    @Test
    void shouldNotSubscribeToExistingTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);
        
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClientService, atMost(1)).subscribeToTopic(anyString(), any());
    }

    @Test
    void shouldUnsubscribeFromTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);

        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        subscriptionService.unsubscribeFromTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        verify(SUBSCRIPTION.get(), times(1)).unsubscribe();
    }

    @Test
    void shouldNotUnsubscribeFromNonexistentTopic() {
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        subscriptionService.unsubscribeFromTopic(CHAIN_TASK_ID);
        verify(SUBSCRIPTION.get(), never()).unsubscribe();
    }

    // region reSubscribeToTopics
    @Test
    void shouldResubscribeToNoTask() {
        subscriptionService.reSubscribeToTopics();

        verify(subscriptionService, never()).subscribeToTopic(any());
    }

    @Test
    void shouldResubscribeToTasks() {
        final ConcurrentHashMap<String, Subscription> chainTaskIdToSubscription = new ConcurrentHashMap<>(Map.of(
                CHAIN_TASK_ID, mock(Subscription.class),
                CHAIN_TASK_ID_2, mock(Subscription.class)
        ));
        ReflectionTestUtils.setField(subscriptionService, "chainTaskIdToSubscription", chainTaskIdToSubscription);

        doNothing().when(subscriptionService).subscribeToTopic(any());

        subscriptionService.reSubscribeToTopics();

        verify(subscriptionService).subscribeToTopic(CHAIN_TASK_ID);
        verify(subscriptionService).subscribeToTopic(CHAIN_TASK_ID_2);
    }
    // endregion

    // region blockMessages
    @Test
    void shouldToggleSessionLost() {
        // Assuming session is ready
        ReflectionTestUtils.setField(subscriptionService, "sessionReady", true);
        assertThat(subscriptionService.isSessionReady()).isTrue();

        // Now the session goes away
        subscriptionService.sessionLost();
        assertThat(subscriptionService.isSessionReady()).isFalse();
    }
    // endregion

    // region waitForSessionReady
    @Test
    void shouldNotWaitIfSessionReady() throws InterruptedException, ExecutionException, TimeoutException {
        // Assuming session is ready
        ReflectionTestUtils.setField(subscriptionService, "sessionReady", true);
        assertThat(subscriptionService.isSessionReady()).isTrue();

        // Then we shouldn't wait for the following call
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                subscriptionService.waitForSessionReady();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        // 10 ms should be enough for the future to complete
        // If it is not, then it is probably stuck somewhere
        future.get(10, TimeUnit.MILLISECONDS);
    }

    @Test
    void shouldWaitForSessionReady() throws ExecutionException, InterruptedException, TimeoutException {
        // Assuming session is NOT ready
        ReflectionTestUtils.setField(subscriptionService, "sessionReady", false);
        assertThat(subscriptionService.isSessionReady()).isFalse();

        // Then we should wait for the following call
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                subscriptionService.waitForSessionReady();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, executor);
        // 10 ms should be enough for the future to complete
        // If it is not, then it is waiting
        assertThatThrownBy(() -> future.get(10, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        // Let's make session ready
        subscriptionService.reSubscribeToTopics();
        assertThat(subscriptionService.isSessionReady()).isTrue();

        // 10 ms should now be enough for the future to complete
        // If it is not, then it is probably stuck somewhere
        future.get(10, TimeUnit.MILLISECONDS);
    }
    // endregion
}
