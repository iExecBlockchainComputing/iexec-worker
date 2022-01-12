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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.utils.AsyncUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import lombok.extern.slf4j.Slf4j;

/**
 * This component handles STOMP websocket connection. First we create an instance of
 * {@link WebSocketStompClient} then we start one and only one listener thread that
 * creates a STOMP session used to subscribe to topics. If an issue occurs with the
 * session the same thread is responsible of refreshing it. The detector of the issue
 * instructs the listener thread to refresh the session by switching an AtomicBoolean
 * flag. This avoids creating multiple sessions at the same time.
 * A scheduled task will periodically check if the watchdog thread is interrupted.
 * If it is the case it will be restarted.
 */
@Slf4j
@Component
public class StompClient {

    // All session requests coming in this time interval
    // will be treated together.
    private static final int SESSION_REFRESH_BACK_OFF_DELAY = 5;

    // A lock used to guarantee that only one thread
    // is listening to session requests.
    private final Lock listenerLock = new Lock();
    // A flag used to watch session requests.
    private final AtomicBoolean isSessionRequested = new AtomicBoolean(false);
    // A dedicated thread executor that handles STOMP session creation
    private final Executor singleThreadExecutor = Executors.newSingleThreadExecutor();
    private final ApplicationEventPublisher eventPublisher;
    private final String webSocketServerUrl;
    private final WebSocketStompClient stompClient;
    private StompSession session;

    public StompClient(ApplicationEventPublisher applicationEventPublisher,
                       CoreConfigurationService coreConfigService, RestTemplate restTemplate) {
        this.eventPublisher = applicationEventPublisher;
        this.webSocketServerUrl = coreConfigService.getUrl() + "/connect";
        log.info("Creating STOMP client");
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        List<Transport> webSocketTransports = Arrays.asList(
                new WebSocketTransport(webSocketClient),
                new RestTemplateXhrTransport(restTemplate)
        );
        SockJsClient sockJsClient = new SockJsClient(webSocketTransports);
        // without SockJS: new WebSocketStompClient(webSocketClient);
        this.stompClient = new WebSocketStompClient(sockJsClient);
        this.stompClient.setAutoStartup(true);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
        log.info("Created STOMP client");
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
        return this.session != null
                ? Optional.of(this.session.subscribe(topic, messageHandler))
                : Optional.empty();
    }

    @PostConstruct
    void init() {
        // Start listener thread for the first time.
        startSessionRequestListenerIfAbsent();
        // Request a STOMP session for the first time
        requestNewSession();
    }

    /**
     * This scheduler will start the listener thread if it happens
     * to be interrupted for whatever reason.
     */
    @Scheduled(initialDelay = 30000, fixedRate = 30000) // 30s, 30s
    void restartSessionRequestListenerIfStopped() {
        startSessionRequestListenerIfAbsent();
    }

    /**
     * Start the thread that listens to session requests in a dedicated
     * thread executor to not block one thread of the default common pool.
     */
    void startSessionRequestListenerIfAbsent() {
        synchronized(listenerLock) {
            if (listenerLock.isLocked()) {
                // Another thread is already listening.
                log.debug("Cannot start a second session request listener");
                return;
            }
            listenerLock.lock();
        }
        AsyncUtils.runAsyncTask("listen-to-stomp-session",
                this::listenToSessionRequests, singleThreadExecutor);
    }

    /**
     * Listen to session request events and refresh the websocket
     * connection by establishing a new STOMP session. All received 
     * requests in a fixed time interval {@code SESSION_REFRESH_DELAY}
     * will be processed only once.
     * <br>
     * 
     * <p><b>Note:</b> the reason we use an AtomicBoolean is because the
     * method {@link SessionHandler#handleTransportError()} is called
     * two times to handle connectivity issues when the websocket
     * connection is, for whatever reason, brutally terminated while
     * a message is being transmitted.
     * The first call is to handle the incomplete body message parsing
     * problem ({@code Premature end of chunk coded message body:
     * closing chunk expected}).
     * The second call happens after the connection is closed in
     * {@link WebSocketHandler#afterConnectionClosed()}.
     * So trying to directly trigger new connection attempts from
     * {@link SessionHandler#handleTransportError()} would result
     * in parallel zombie threads trying to establish a new session
     * each.
     * Instead, each call to this method changes the flag
     * {@code isSessionRequested} to true. We wait for a short
     * period of time to collect all these requests coming from
     * different calls (and possibly different threads), then we send
     * only one request to the server. This process is repeated until
     * the websocket connection is reestablished again.
     */
    void listenToSessionRequests() {
        log.info("Listening to incoming STOMP session requests");
        while (!Thread.interrupted()) {
            try {
                if (!isSessionRequested.get()) {
                    // No requests
                    continue;
                }
                // wait some time for the wave of request events coming
                // from possibly different threads to finish
                backOff();
                // Switch the flag back to mark requests as treated.
                isSessionRequested.set(false);
                // Send one request to the server.
                createSession();
            } catch(InterruptedException e) {
                log.error("STOMP session request listener got interrupted", e);
                // Unlock flag so another thread can be started.
                listenerLock.unlock();
                // Properly handle InterruptedException.
                Thread.currentThread().interrupt();
                // The thread will stop
            } catch(Throwable t) {
                // Ignore all errors and continue
                log.error("An error occurred while listening to STOMP session requests", t);
                // The thread will continue
            }
        }
    }

    /**
     * Change the flag {@code isSessionRequested} to true. A listener
     * that is watching this flag will create a new STOMP session.
     */
    void requestNewSession() {
        isSessionRequested.set(true);
        log.info("Requested a new STOMP session");
    }

    /**
     * Establish connection to the WebSocket Server.
     */
    void createSession() {
        log.info("Creating new STOMP session");
        this.stompClient.connect(webSocketServerUrl, new SessionHandler());
    }

    /**
     * Sleep {@link StompClient#SESSION_REFRESH_BACK_OFF_DELAY} seconds.
     * 
     * @throws InterruptedException
     */
    void backOff() throws InterruptedException {
        TimeUnit.SECONDS.sleep(SESSION_REFRESH_BACK_OFF_DELAY);
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
            StompClient.this.session = session;
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
            requestNewSession();
        }
    }

    private static class Lock {
        private final AtomicBoolean value = new AtomicBoolean(false);

        boolean isLocked() {
            return value.get();
        }

        void lock() {
            value.set(true);
        }

        void unlock() {
            value.set(false);
        }
    }
}
