package com.iexec.worker.pubsub;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {

    private final String coreHost;
    private final int corePort;
    private final String workerWalletAddress;

    // external services
    private TaskExecutorService taskExecutorService;

    // internal components
    private StompSession session;
    private Map<String, StompSession.Subscription> chainTaskIdToSubscription;
    private WebSocketStompClient stompClient;
    private String url;

    public SubscriptionService(CoreConfigurationService coreConfigurationService,
                               WorkerConfigurationService workerConfigurationService,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService,
                               RevealService revealService,
                               CustomFeignClient feignClient,
                               PublicConfigurationService publicConfigurationService,
                               CredentialsService credentialsService,
                               TaskExecutorService taskExecutorService) {
        this.taskExecutorService = taskExecutorService;

        this.coreHost = coreConfigurationService.getHost();
        this.corePort = coreConfigurationService.getPort();
        this.workerWalletAddress = workerConfigurationService.getWorkerWalletAddress();

        chainTaskIdToSubscription = new ConcurrentHashMap<>();
        url = "ws://" + coreHost + ":" + corePort + "/connect";
    }

    @PostConstruct
    private void run() {
        this.restartStomp();
    }

    private void restartStomp() {
        log.info("Starting STOMP");
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        this.stompClient = new WebSocketStompClient(webSocketClient);
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.stompClient.setTaskScheduler(new ConcurrentTaskScheduler());
        this.stompClient.connect(url, this);
        log.info("Started STOMP");
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
        if (!chainTaskIdToSubscription.containsKey(chainTaskId)) {
            StompSession.Subscription subscription = session.subscribe(getTaskTopicName(chainTaskId), new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return TaskNotification.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                    if (payload != null) {
                        TaskNotification taskNotification = (TaskNotification) payload;
                        handleTaskNotification(taskNotification);
                    } else {
                        log.info("Payload of TaskNotification is null [chainTaskId:{}]", chainTaskId);
                    }
                }
            });
            chainTaskIdToSubscription.put(chainTaskId, subscription);
            log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
        } else {
            log.info("Already subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
        }
    }

    public void unsubscribeFromTopic(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
            chainTaskIdToSubscription.remove(chainTaskId);
            log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        }
    }

    private void handleTaskNotification(TaskNotification notif) {
        if (notif.getWorkersAddress().contains(workerWalletAddress)
                || notif.getWorkersAddress().isEmpty()) {
            log.info("Received notification [notification:{}]", notif);

            TaskNotificationType type = notif.getTaskNotificationType();
            String chainTaskId = notif.getChainTaskId();

            switch (type) {
                case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                    taskExecutorService.abortContributionTimeout(chainTaskId);
                    break;

                case PLEASE_ABORT_CONSENSUS_REACHED:
                    taskExecutorService.abortConsensusReached(chainTaskId);
                    break;

                case PLEASE_REVEAL:
                    taskExecutorService.reveal(chainTaskId);
                    break;

                case PLEASE_UPLOAD:
                    taskExecutorService.uploadResult(chainTaskId);
                    break;

                case COMPLETED:
                    taskExecutorService.completeTask(chainTaskId);
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