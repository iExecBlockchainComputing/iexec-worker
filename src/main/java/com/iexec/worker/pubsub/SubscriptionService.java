package com.iexec.worker.pubsub;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.config.WorkerConfigurationService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {


    private WorkerConfigurationService workerConfigurationService;
    private ApplicationEventPublisher applicationEventPublisher;

    // internal components
    private StompClient stompClient;
    private Map<String, StompSession.Subscription> chainTaskIdToSubscription;

    public SubscriptionService(WorkerConfigurationService workerConfigurationService,
                               ApplicationEventPublisher applicationEventPublisher,
                               StompClient stompClient) {
        this.workerConfigurationService = workerConfigurationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.stompClient = stompClient;
        chainTaskIdToSubscription = new ConcurrentHashMap<>();
    }

    public void reSubscribeToTopics() {
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
        MessageHandler messageHandler = new MessageHandler(chainTaskId);
        StompSession.Subscription subscription = stompClient.subscribeToTopic(getTaskTopicName(chainTaskId), messageHandler);
        chainTaskIdToSubscription.put(chainTaskId, subscription);
        log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
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

    private void unsubscribeFromTopic(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
            chainTaskIdToSubscription.remove(chainTaskId);
            log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        }
    }

    private String getTaskTopicName(String chainTaskId) {
        return "/topic/task/" + chainTaskId;
    }

    @AllArgsConstructor
    public class MessageHandler implements StompFrameHandler {

        private final String chainTaskId;

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return TaskNotification.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, @Nullable Object payload) {
            if (payload == null) {
                log.info("Payload of TaskNotification is null [chainTaskId:{}]", this.chainTaskId);
                return;
            }
            TaskNotification taskNotification = (TaskNotification) payload;
            boolean isNotifForEveryone = taskNotification.getWorkersAddress().isEmpty();
            boolean isNotifForMe = taskNotification.getWorkersAddress()
                    .contains(workerConfigurationService.getWorkerWalletAddress());
            if (!isNotifForEveryone && !isNotifForMe) {
                return;
            }
            log.info("PubSub service received taskNotification [chainTaskId:{}, type:{}]",
                    this.chainTaskId, taskNotification.getTaskNotificationType());
            applicationEventPublisher.publishEvent(taskNotification);
        }
    }
}