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

import com.iexec.worker.config.SchedulerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class StompClientServiceTests {
    private static final String SESSION_ID = "SESSION_ID";

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    SchedulerConfiguration schedulerConfiguration;

    @Mock
    WebSocketStompClient stompClient;

    @Mock
    StompSession mockedStompSession;

    @Spy
    @InjectMocks
    private StompClientService stompClientService;

    //region subscribeToTopic
    @Test
    void shouldNotSubscribeToTopicWhenOneParamIsNull() {
        assertThrows(NullPointerException.class,
                () -> stompClientService.subscribeToTopic("topic", null));
        assertThrows(NullPointerException.class,
                () -> stompClientService.subscribeToTopic(null, mock(StompFrameHandler.class)));
    }

    @Test
    void shouldNotSubscribeToTopicWhenSessionIsNull() {
        assertThat(stompClientService.subscribeToTopic("topic", mock(StompFrameHandler.class))).isEmpty();
    }

    @Test
    void shouldSubscribeToTopic() {
        StompSession stompSession = mock(StompSession.class);
        ReflectionTestUtils.setField(stompClientService, "stompSession", stompSession);
        StompFrameHandler messageHandler = mock(SubscriptionService.MessageHandler.class);
        StompSession.Subscription subscription = mock(StompSession.Subscription.class);
        when(stompSession.subscribe("topic", messageHandler)).thenReturn(subscription);
        assertThat(stompClientService.subscribeToTopic("topic", messageHandler))
                .isNotEmpty()
                .contains(subscription);
    }
    //endregion

    // region createStompSessionIfDisconnected
    @Test
    void shouldNotCreateStompSessionIfAlreadyConnected(CapturedOutput output) {
        final StompSession connectedSession = mock(StompSession.class);
        when(connectedSession.isConnected()).thenReturn(true);
        when(connectedSession.getSessionId()).thenReturn(SESSION_ID);
        ReflectionTestUtils.setField(stompClientService, "stompSession", connectedSession);

        final String sessionId = stompClientService.createStompSessionIfDisconnected();
        assertThat(sessionId).isEqualTo(SESSION_ID);
        assertThat(output.getOut()).contains("A valid STOMP session exists, ignoring this request");
    }

    @Test
    void shouldCreateStompSessionIfNoSession(CapturedOutput output) {
        final SettableListenableFuture<StompSession> futureSession = new SettableListenableFuture<>();
        futureSession.set(mockedStompSession);
        when(stompClient.connect(any(), any())).thenReturn(futureSession);
        when(mockedStompSession.getSessionId()).thenReturn(SESSION_ID);

        final String sessionId = stompClientService.createStompSessionIfDisconnected();
        assertThat(sessionId).isEqualTo(SESSION_ID);
        assertThat(output.getOut()).contains("Creating new STOMP session");
    }

    @Test
    void shouldCreateStompSessionIfDisconnected(CapturedOutput output) {
        final StompSession disconnectedSession = mock(StompSession.class);
        when(disconnectedSession.isConnected()).thenReturn(false);
        ReflectionTestUtils.setField(stompClientService, "stompSession", disconnectedSession);

        final SettableListenableFuture<StompSession> futureSession = new SettableListenableFuture<>();
        futureSession.set(mockedStompSession);
        when(stompClient.connect(any(), any())).thenReturn(futureSession);
        when(mockedStompSession.getSessionId()).thenReturn(SESSION_ID);

        final String sessionId = stompClientService.createStompSessionIfDisconnected();
        assertThat(sessionId).isEqualTo(SESSION_ID);
        assertThat(output.getOut()).contains("Creating new STOMP session");
    }

    @Test
    void shouldNotCreateStompSessionButThrowIfTimeout(CapturedOutput output) throws ExecutionException, InterruptedException, TimeoutException {
        final SettableListenableFuture<StompSession> futureSession = getMockedFutureSession();
        when(stompClient.connect(any(), any())).thenReturn(futureSession);
        when(futureSession.get(anyLong(), any())).thenThrow(TimeoutException.class);

        assertThat(stompClientService.createStompSessionIfDisconnected()).isNull();
        assertThat(output.getOut()).contains("STOMP session creation timed out");
    }

    @Test
    void shouldNotCreateStompSessionButThrowIfAnyException(CapturedOutput output) throws ExecutionException, InterruptedException, TimeoutException {
        final SettableListenableFuture<StompSession> futureSession = getMockedFutureSession();
        when(stompClient.connect(any(), any())).thenReturn(futureSession);
        when(futureSession.get(anyLong(), any())).thenThrow(InterruptedException.class);

        assertThat(stompClientService.createStompSessionIfDisconnected()).isNull();
        assertThat(output.getOut()).contains("An error occurred while listening to STOMP session requests");
    }

    @SuppressWarnings("unchecked")
    private static SettableListenableFuture<StompSession> getMockedFutureSession() {
        return mock(SettableListenableFuture.class);
    }
    // endregion
}
