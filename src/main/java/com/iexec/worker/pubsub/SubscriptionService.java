package com.iexec.worker.pubsub;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
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

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {

    private RestTemplate restTemplate;
    // external services
    private CoreConfigurationService coreConfigurationService;
    private WorkerConfigurationService workerConfigurationService;
    private ApplicationEventPublisher applicationEventPublisher;

    // internal components
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
        this.restartStomp();
    }

    private void restartStomp() {
        log.info("Starting STOMP");
        if (isConnectEndpointUp()) {
            WebSocketClient webSocketClient = new StandardWebSocketClient();
            List<Transport> webSocketTransports = Arrays.asList(new WebSocketTransport(webSocketClient),
                    new RestTemplateXhrTransport(restTemplate));
            SockJsClient sockJsClient = new SockJsClient(webSocketTransports);
            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);//without SockJS: new WebSocketStompClient(webSocketClient);
            stompClient.setAutoStartup(true);
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());
            stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
            stompClient.connect(url, this);
            log.info("Started STOMP");
        }
    }

    private boolean isConnectEndpointUp() {
        ResponseEntity<String> checkConnectionEntity = restTemplate.getForEntity(url, String.class);
        if (checkConnectionEntity.getStatusCode().is2xxSuccessful()) {
            return true;
        }
        log.error("isConnectEndpointUp failed (will retry) [url:{}, status:{}]", url, checkConnectionEntity.getStatusCode());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return isConnectEndpointUp();
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

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("SubscriptionService set up [session: {}, isConnected: {}]", session.getSessionId(), session.isConnected());
        this.session = session;
        this.reSubscribeToTopics();
    }

    @Override
    public void handleException(StompSession session, @Nullable StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        SimpMessageType messageType = null;
        if (command != null) {
            messageType = command.getMessageType();
        }
        log.error("Received handleException [session: {}, isConnected: {}, command: {}, exception: {}]",
                session.getSessionId(), session.isConnected(), messageType, exception.getMessage());
        exception.printStackTrace();
        this.restartStomp();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.info("Received handleTransportError [session: {}, isConnected: {}, exception: {}]",
                session.getSessionId(), session.isConnected(), exception.getMessage());
        exception.printStackTrace();
        this.restartStomp();
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
                boolean isForEveryone = taskNotification.getWorkersAddress().isEmpty();
                boolean isForMe = taskNotification.getWorkersAddress()
                        .contains(workerConfigurationService.getWorkerWalletAddress());

                if (!isForEveryone && !isForMe) {
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