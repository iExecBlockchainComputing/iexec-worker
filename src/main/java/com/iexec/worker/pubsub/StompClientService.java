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

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.utils.AsyncUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This component handles STOMP websocket connection.
 * A scheduled task regularly checks whether a STOMP session is active,
 * and (re)creates it if not.
 */
@Slf4j
@Component
public class StompClientService {
    private static final int SESSION_REFRESH_DELAY_MS = 5000;
    private final Executor singleThreadExecutor = Executors.newSingleThreadExecutor();
    private final ApplicationEventPublisher eventPublisher;
    private final String webSocketServerUrl;
    private final WebSocketStompClient stompClient;
    private StompSession stompSession;

    public StompClientService(ApplicationEventPublisher applicationEventPublisher,
                              CoreConfigurationService coreConfigService,
                              WebSocketStompClient stompClient) {
        this.eventPublisher = applicationEventPublisher;
        this.webSocketServerUrl = coreConfigService.getUrl() + "/connect";
        this.stompClient = stompClient;
    }

    /**
     * Subscribe to a topic and provide a {@link StompFrameHandler}
     * to handle received messages.
     * 
     * @param topic
     * @param messageHandler an implementation of {@link StompFrameHandler}
     * @return
     */
    Optional<Subscription> subscribeToTopic(String topic, StompFrameHandler messageHandler) {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        // Should not let other threads subscribe
        // when the session is not ready yet.
        return stompSession != null
                ? Optional.of(stompSession.subscribe(topic, messageHandler))
                : Optional.empty();
    }

    @EventListener(ApplicationStartedEvent.class)
    @Scheduled(fixedRate = SESSION_REFRESH_DELAY_MS)
    void scheduleStompSessionCreation() {
        AsyncUtils.runAsyncTask("listen-to-stomp-session",
                this::createStompSessionIfDisconnected, singleThreadExecutor);
    }

    /**
     * Create a new STOMP session
     * if none exists or the last one is disconnected.
     * @return A {@link String} representing the session ID.
     */
    String createStompSessionIfDisconnected() {
        if (stompSession != null && stompSession.isConnected()) {
            log.debug("A valid STOMP session exists, ignoring this request");
            return stompSession.getSessionId();
        }

        try {
            log.info("Creating new STOMP session");
            this.stompSession = stompClient
                    .connect(webSocketServerUrl, new SessionHandler())
                    .get(SESSION_REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("STOMP session creation timed out [timeout:{}ms]", SESSION_REFRESH_DELAY_MS, e);
            return null;
        } catch (Exception e) {
            // If any other error, we log it and interrupt current thread.
            // This prevents anything else to execute
            // because we may have encounter an InterruptedException.
            log.error("An error occurred while listening to STOMP session requests", e);
            Thread.currentThread().interrupt();
            return null;
        }

        return stompSession.getSessionId();
    }

    /**
     * Provide callbacks to handle STOMP session establishment or
     * failure.
     */
    private class SessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.info("Connected to STOMP session [session: {}, isConnected: {}]",
                    session.getSessionId(), session.isConnected());
            // notify subscribers
            eventPublisher.publishEvent(new SessionCreatedEvent());
        }

        /**
         * Handle any exception arising while processing a STOMP frame such as a
         * failure to convert the payload or an unhandled exception in the
         * application {@code StompFrameHandler}.
         * 
         * @param session the client STOMP session
         * @param command the STOMP command of the frame
         * @param headers the headers
         * @param payload the raw payload
         * @param exception the exception
         */
        @Override
        public void handleException(StompSession session, @Nullable StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            SimpMessageType messageType = command != null ? command.getMessageType() : null;
            log.error("STOMP frame processing error [session: {}, isConnected: {}, command: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
        }

        /**
         * Handle a low level transport error which could be an I/O error or a
         * failure to encode or decode a STOMP message.
         * <p>Note that
         * {@link org.springframework.messaging.simp.stomp.ConnectionLostException
         * ConnectionLostException} will be passed into this method when the
         * connection is lost rather than closed normally via
         * {@link StompSession#disconnect()}.
         * 
         * @param session the client STOMP session
         * @param exception the exception that occurred
         */
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("STOMP transport error [session: {}, isConnected: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), exception.getMessage());
            // notify subscribers
            eventPublisher.publishEvent(new SessionLostEvent());
        }
    }

}
