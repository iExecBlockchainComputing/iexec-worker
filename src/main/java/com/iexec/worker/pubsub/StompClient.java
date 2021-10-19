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

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StompClient {

    private static final int SESSION_REFRESH_DELAY = 5;
    private final BlockingQueue<SessionRequestEvent> sessionRequestQueue = new ArrayBlockingQueue<>(1);
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
     * @param messageHandler an implementation of 
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
        // Start the thread that listens to session requests in a dedicated thread executor
        // to not block one thread of the default common pool
        AsyncUtils.runAsyncTask("listen-to-stomp-session",
                this::listenToSessionRequests, singleThreadExecutor);
        // Request a STOMP session for the first time
        requestNewSession();
    }

    /**
     * Add new SessionRequestEvent to the queue. A queue listener
     * will consume this event and create a new STOMP session.
     * This does not raise an error if the queue is full.
     */
    void requestNewSession() {
        this.sessionRequestQueue.offer(new SessionRequestEvent());
        log.info("Requested a new STOMP session");
    }

    /**
     * Listen to session request events and refresh the websocket
     * connection by establishing a new STOMP session. Only one of 
     * the received requests in a fixed time interval
     * {@code SESSION_REFRESH_DELAY} will be processed.
     * <br>
     * 
     * <p><b>Note:</b> the reason we use a queue is because the
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
     * Instead, each call to this method adds a
     * {@link SessionRequestEvent} to the queue. We wait for a short
     * period of time to collect all these requests coming from
     * different calls (and possibly different threads), then we send
     * only one request to the server. This process is repeated until
     * the websocket connection is reestablished again.
     */
    void listenToSessionRequests() {
        log.info("Listening to incoming STOMP session requests");
        while (!Thread.interrupted()) {
            try {
                // get the first request event or wait until available
                this.sessionRequestQueue.take();
                // wait some time for the wave of request events coming
                // from possibly different threads to finish
                TimeUnit.SECONDS.sleep(SESSION_REFRESH_DELAY);
                // purge redundant request events
                this.sessionRequestQueue.clear();
                // Only one attempt should pass through
                createSession();
            } catch(InterruptedException e) {
                log.error("STOMP session request listener got interrupted", e);
                Thread.currentThread().interrupt();
                // The thread will stop
            } catch(Throwable t) {
                log.error("An error occurred while listening to STOMP session requests", t);
                // The thread will continue
            }
        }
    }

    /**
     * Establish connection to the WebSocket Server.
     */
    void createSession() {
        log.info("Creating new STOMP session");
        this.stompClient.connect(webSocketServerUrl, new SessionHandler());
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

    /**
     * Request a new STOMP session by adding
     * this event to the queue.
     */
    @NoArgsConstructor
    private class SessionRequestEvent {
        
    }
}
