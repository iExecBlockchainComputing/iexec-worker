/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTests {

    @Mock
    private Subscription subscription;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private StompClientService stompClientService;

    @Spy
    @InjectMocks
    SubscriptionService subscriptionService;

    private static final String CHAIN_TASK_ID = "chaintaskid";
    private static final String CHAIN_TASK_ID_2 = "chaintaskid2";

    // region subscribe
    @Test
    void shouldSubscribeToTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(Optional.of(subscription));

        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClientService, times(1)).subscribeToTopic(anyString(), any());
    }

    @Test
    void shouldNotSubscribeToExistingTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(Optional.of(subscription));

        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClientService, atMost(1)).subscribeToTopic(anyString(), any());
    }
    // endregion

    // region unsubscribe
    @Test
    void shouldUnsubscribeFromTopic() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(Optional.of(subscription));

        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        assertThat(subscriptionService.purgeTask(CHAIN_TASK_ID)).isTrue();
        verify(subscription, times(1)).unsubscribe();
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
    }

    @Test
    void shouldNotUnsubscribeFromNonexistentTopic() {
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        assertThat(subscriptionService.purgeTask(CHAIN_TASK_ID)).isTrue();
        verify(subscription, never()).unsubscribe();
    }
    // endregion

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

    // region purgeAllTasksData
    @Test
    void shouldPurgeMultipleTasks() {
        when(stompClientService.subscribeToTopic(anyString(), any())).thenReturn(Optional.of(subscription));

        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID_2);

        subscriptionService.purgeAllTasksData();

        verify(subscription, times(2)).unsubscribe();
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID_2)).isFalse();
    }

    @Test
    void shouldHandleEmptyPurgeAllTasksData() {
        subscriptionService.purgeAllTasksData();
        verify(subscription, never()).unsubscribe();
    }
    // endregion
}
