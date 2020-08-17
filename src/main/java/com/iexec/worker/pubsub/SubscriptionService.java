package com.iexec.worker.pubsub;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
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
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {

    private static final int REFRESH_PERIOD_IN_SECONDS = 5;

    private RestTemplate restTemplate;
    // external services
    private CoreConfigurationService coreConfigurationService;
    private WorkerConfigurationService workerConfigurationService;
    private ApplicationEventPublisher applicationEventPublisher;

    // internal components
    private WebSocketStompClient stompClient;
    private ExecutorService stompConnectionRequestThread;
    private StompSession session;
    private Map<String, StompSession.Subscription> chainTaskIdToSubscription;
    private String url;

    public SubscriptionService(CoreConfigurationService coreConfigurationService,
                               WorkerConfigurationService workerConfigurationService,
                               RestTemplate restTemplate,
                               ApplicationEventPublisher applicationEventPublisher) {
        this.restTemplate = restTemplate;
        this.coreConfigurationService = coreConfigurationService;
        this.workerConfigurationService = workerConfigurationService;
        this.applicationEventPublisher = applicationEventPublisher;
        chainTaskIdToSubscription = new ConcurrentHashMap<>();
    }

    @PostConstruct
    void init() {
        this.url = coreConfigurationService.getUrl() + "/connect";
        createStompClient();
        connect();
    }

    private void createStompClient() {
        log.info("Creating STOMP client");
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        List<Transport> webSocketTransports = Arrays.asList(
                new WebSocketTransport(webSocketClient),
                new RestTemplateXhrTransport(restTemplate)
        );
        SockJsClient sockJsClient = new SockJsClient(webSocketTransports);
        stompClient = new WebSocketStompClient(sockJsClient); // without SockJS: new WebSocketStompClient(webSocketClient);
        stompClient.setAutoStartup(true);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
        log.info("Created STOMP client");
    }

    private void connect() {
        log.info("Sending STOMP connection request");
        if (stompClient.connect(url, this).completable().isCompletedExceptionally()) {
            log.error("STOMP connection request failed");
        }
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("Connected to STOMP session [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
        shutdownCurrentStompConnectionRequestThread();
        this.session = session;
        reSubscribeToTopics();
    }

    private void reSubscribeToTopics() {
        List<String> chainTaskIds = new ArrayList<>(chainTaskIdToSubscription.keySet());
        log.info("ReSubscribing to topics [chainTaskIds: {}]", chainTaskIds.toString());
        for (String chainTaskId : chainTaskIds) {
            chainTaskIdToSubscription.remove(chainTaskId);
            subscribeToTopic(chainTaskId);
        }
        log.info("ReSubscribed to topics [chainTaskIds: {}]", chainTaskIds.toString());
    }

    public void subscribeToTopic(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            log.info("Already subscribed to topic [chainTaskId:{}, topic:{}]",
                    chainTaskId, getTaskTopicName(chainTaskId));
            return;
        }

        StompFrameHandler stompFrameHandler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskNotification.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                if (payload == null) {
                    log.info("Payload of TaskNotification is null [chainTaskId:{}]", chainTaskId);
                    return;
                }

                TaskNotification taskNotification = (TaskNotification) payload;
                boolean isNotifForEveryone = taskNotification.getWorkersAddress().isEmpty();
                boolean isNotifForMe = taskNotification.getWorkersAddress()
                        .contains(workerConfigurationService.getWorkerWalletAddress());

                if (!isNotifForEveryone && !isNotifForMe) {
                    return;
                }

                log.info("PubSub service received taskNotification [chainTaskId:{}, type:{}]", chainTaskId, taskNotification.getTaskNotificationType());
                applicationEventPublisher.publishEvent(taskNotification);
            }
        };

        StompSession.Subscription subscription = session.subscribe(getTaskTopicName(chainTaskId), stompFrameHandler);
        chainTaskIdToSubscription.put(chainTaskId, subscription);
        log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        SimpMessageType messageType = command != null ? command.getMessageType() : null;
        log.error("STOMP error [session: {}, isConnected: {}, command: {}, exception: {}]",
                session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
        refresh();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.error("STOMP transport error [session: {}, isConnected: {}, exception: {}]",
                session.getSessionId(), session.isConnected(), exception.getMessage());
        refresh();
    }

    private void refresh() {
        shutdownCurrentStompConnectionRequestThread();
        stompConnectionRequestThread = Executors.newSingleThreadExecutor();
        CompletableFuture.runAsync(
            () -> {
                Thread.currentThread().setName("stomp-conn-req");
                try {
                    log.info("Refreshing STOMP session in {}s", REFRESH_PERIOD_IN_SECONDS);
                    TimeUnit.SECONDS.sleep(REFRESH_PERIOD_IN_SECONDS);
                    connect();
                } catch (Exception e) {
                    log.error("Interrupted while sleeping [exception:{}]", e.getMessage());
                }
            },
            stompConnectionRequestThread)
        .exceptionally((t) -> {
            t.printStackTrace();
            return null;
        });
    }

    private void shutdownCurrentStompConnectionRequestThread() {
        if (stompConnectionRequestThread != null) {
            stompConnectionRequestThread.shutdownNow();
        }
    }

    void unsubscribeFromTopic(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
            chainTaskIdToSubscription.remove(chainTaskId);
            log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        }
    }

    public void handleSubscription(TaskNotification notif) {
        if (notif.getWorkersAddress() != null &&
                (notif.getWorkersAddress().contains(workerConfigurationService.getWorkerWalletAddress())
                        || notif.getWorkersAddress().isEmpty())) {
            log.info("Received notification [notification:{}]", notif);

            TaskNotificationType action = notif.getTaskNotificationType();
            String chainTaskId = notif.getChainTaskId();

            switch (action){
                /* Subscribe if not ?
                case PLEASE_START:
                case PLEASE_DOWNLOAD_APP:
                case PLEASE_DOWNLOAD_DATA:
                case PLEASE_COMPUTE:
                case PLEASE_CONTRIBUTE:
                case PLEASE_REVEAL:
                case PLEASE_UPLOAD:
                    subscribeToTopic(chainTaskId);
                    break;
                 */
                case PLEASE_COMPLETE:
                case PLEASE_ABORT:
                case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                case PLEASE_ABORT_CONSENSUS_REACHED:
                    unsubscribeFromTopic(chainTaskId);
                    break;
                default:
                    break;
            }
        }
    }

    private String getTaskTopicName(String chainTaskId) {
        return "/topic/task/" + chainTaskId;
    }
}