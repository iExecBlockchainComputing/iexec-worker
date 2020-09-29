package com.iexec.worker.pubsub;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.pubsub.SubscriptionService.MessageHandler;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final int SESSION_REFRESH_DELAY = 10;
    private final BlockingQueue<SessionRequestEvent> sessionRequestQueue = new ArrayBlockingQueue<>(1);
    // private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final ApplicationEventPublisher eventPublisher;
    private final String webSocketUrl;
    private final WebSocketStompClient stompClient;
    private StompSession session;

    public StompClient(ApplicationEventPublisher applicationEventPublisher,
                       RestTemplate restTemplate, CoreConfigurationService coreConfigService) {
        this.eventPublisher = applicationEventPublisher;
        this.webSocketUrl = coreConfigService.getUrl() + "/connect";
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

    StompSession.Subscription subscribeToTopic(String topic, MessageHandler messageHandler) {
        return this.session.subscribe(topic, messageHandler);
    }

    @PostConstruct
    private void init() {
        requestNewSession();
    }

    /**
     * Add new SessionRequestEvent to the queue. A queue listener
     * will consume this event and create a new STOMP session.
     * 
     */
    private void requestNewSession() {
        this.sessionRequestQueue.offer(new SessionRequestEvent());
    }

    /**
     * Refresh the websocket connection by establishing a new STOMP session.
     * Only one of the received requests in a fixed time interval
     * (REFRESH_PERIOD_IN_SECONDS) will be processed. We use @Scheduled
     * to start the watcher asynchronously with an initial delay.
     * 
     * @throws InterruptedException
     * 
     */
    @Scheduled(initialDelay = 1000, fixedDelay = 10000)
    private void sessionRequestEventListener() throws InterruptedException {
        while (true) {
            // get the first request event
            this.sessionRequestQueue.take();
            // wait some time for the wave of request events coming
            // from possibly different threads to finish
            log.info("Creating new STOMP session in {}s", SESSION_REFRESH_DELAY);
            TimeUnit.SECONDS.sleep(SESSION_REFRESH_DELAY);
            // purge redundant request events
            this.sessionRequestQueue.clear();
            // Only one request should pass through
            log.info("Sending new STOMP connection request");
            this.stompClient.connect(webSocketUrl, new SessionHandler());
        }
    }

    /**
     * Provide callbacks to handle STOMP session establishment or
     * connection failure.
     * 
     */
    private class SessionHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.info("Connected to STOMP session [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
            StompClient.this.session = session;
            // notify subscribers
            eventPublisher.publishEvent(new SessionCreatedEvent());
        }

        @Override
        public void handleException(StompSession session, @Nullable StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            SimpMessageType messageType = command != null ? command.getMessageType() : null;
            log.error("STOMP error [session: {}, isConnected: {}, command: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("STOMP transport error [session: {}, isConnected: {}, exception: {}]",
                    session.getSessionId(), session.isConnected(), exception.getMessage());
            requestNewSession();
        }
    }

    /**
     * SessionRequest
     */
    private class SessionRequestEvent {
        
    }
}