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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iexec.worker.config.WorkerConfigurationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;

import java.util.Optional;

public class SubscriptionServiceTests {

    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private StompClient stompClient;

    @InjectMocks
    SubscriptionService subscriptionService;

    private static final String WORKER_WALLET_ADDRESS = "0x1234";
    private static final String CHAIN_TASK_ID = "chaintaskid";
    private static final Optional<Subscription> SUBSCRIPTION =
            Optional.of(mock(Subscription.class));

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(workerConfigurationService.getWorkerWalletAddress())
                .thenReturn(WORKER_WALLET_ADDRESS);
    }

    @Test
    public void shouldSubscribeToTopic() {
        when(stompClient.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);

        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClient, times(1)).subscribeToTopic(anyString(), any());
    }

    @Test
    public void shouldNotSubscribeToExistingTopic() {
        when(stompClient.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);
        
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        verify(stompClient, atMost(1)).subscribeToTopic(anyString(), any());
    }

    @Test
    public void shouldUnsubscribeFromTopic() {
        when(stompClient.subscribeToTopic(anyString(), any())).thenReturn(SUBSCRIPTION);

        subscriptionService.subscribeToTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isTrue();
        subscriptionService.unsubscribeFromTopic(CHAIN_TASK_ID);
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        verify(SUBSCRIPTION.get(), times(1)).unsubscribe();
    }

    @Test
    public void shouldNotUnsubscribeFromInexistentTopic() {
        assertThat(subscriptionService.isSubscribedToTopic(CHAIN_TASK_ID)).isFalse();
        subscriptionService.unsubscribeFromTopic(CHAIN_TASK_ID);
        verify(SUBSCRIPTION.get(), never()).unsubscribe();
    }
}
