package com.iexec.worker.pubsub;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.pubsub.SubscriptionService.MessageHandler;

import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StompClient {

    private static final int REFRESH_PERIOD_IN_SECONDS = 5;
    private final ConnectionRequestMutex connectionRequestMutex = new ConnectionRequestMutex();
    private final WebSocketStompClient stompClient;
    private final String webSocketUrl;
    private StompSession session;

    private StompClient(RestTemplate restTemplate, CoreConfigurationService coreConfigService) {
        this.webSocketUrl = coreConfigService.getUrl() + "/connect";
        log.info("Creating STOMP client");
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        List<Transport> webSocketTransports = Arrays.asList(
                new WebSocketTransport(webSocketClient),
                new RestTemplateXhrTransport(restTemplate)
        );
        SockJsClient sockJsClient = new SockJsClient(webSocketTransports);
        this.stompClient = new WebSocketStompClient(sockJsClient); // without SockJS: new WebSocketStompClient(webSocketClient);
        this.stompClient.setAutoStartup(true);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
        log.info("Created STOMP client");
    }

    public StompSession.Subscription subscribeToTopic(String topic, MessageHandler messageHandler) {
        return this.session.subscribe(topic, messageHandler);
    }

    @PostConstruct
    private void initConnection() {
        connect();
    }

    /**
     * Establish a STOMP session. Only one request should be
     * allowed at the same time. The lock is released
     * asynchronously when the request is successful by
     * {@link SessionHandler}.afterConnected() or when an error
     * occurs by {@link SessionHandler}.handleTransportError().
     */
    private void connect() {
        if (connectionRequestMutex.isLocked()) {
            return;
        }
        connectionRequestMutex.lock();
        log.info("Sending STOMP connection request");
        stompClient.connect(webSocketUrl, new SessionHandler());
        log.info("STOMP connection sent");
    }

    // private void refreshSession() {
    //     log.info("Refreshing STOMP session in {}s", REFRESH_PERIOD_IN_SECONDS);
    //     sleep();
    //     connect();
    // }

    // private boolean isAlreadyRequestingConnection() {
    //     return this.connectionRequestMutex.get();
    // }

    // private void lockConnectionRequestMutex() {
    //     this.connectionRequestMutex.set(true);
    // }

    // private void releaseConnectionRequestMutex() {
    //     this.connectionRequestMutex.set(false);
    // }

    private void sleep() {
        try {
            TimeUnit.SECONDS.sleep(REFRESH_PERIOD_IN_SECONDS);
        } catch (Exception e) {
            log.error("Interrupted while sleeping [exception:{}]", e.getMessage());
        }
    }

    /**
     * This Mutex is used to drop new connection requests
     * if an existing request is still going on.
     */
    private class ConnectionRequestMutex {
        
        private final AtomicBoolean connectionRequestMutex;

        private ConnectionRequestMutex() {
            this.connectionRequestMutex = new AtomicBoolean(false);
        }

        public boolean isLocked() {
            return this.connectionRequestMutex.get();
        }

        public void lock() {
            this.connectionRequestMutex.set(true);
        }

        public void release() {
            this.connectionRequestMutex.set(false);
        }
    
    }

    /**
     * Provide callbacks to handle STOMP session establishment or
     * connection failure.
     */
    private class SessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.info("Connected to STOMP session [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
            StompClient.this.session = session;
            StompClient.this.connectionRequestMutex.release();
            // TODO notifySubscribers()
        }

        @Override
        public void handleException(StompSession session, @Nullable StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            SimpMessageType messageType = command != null ? command.getMessageType() : null;
            log.error("STOMP error [session: {}, isConnected: {}, command: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
        }

        /**
         * Refresh
         */
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("STOMP transport error [session: {}, isConnected: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), exception.getMessage());
            StompClient.this.connectionRequestMutex.release();
            // refreshSession();
            log.info("Refreshing STOMP session in {}s", REFRESH_PERIOD_IN_SECONDS);
            sleep();
            connect();    
        }
    }    
}