package com.iexec.worker.pubsub;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.iexec.common.notification.TaskNotification;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.pubsub.StompClient.SessionCreatedEvent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SubscriptionService {

    private final Map<String, Subscription> chainTaskIdToSubscription = new ConcurrentHashMap<>();
    private final String workerWalletAddress;
    private final ApplicationEventPublisher eventPublisher;
    private final StompClient stompClient;

    public SubscriptionService(WorkerConfigurationService workerConfigurationService,
                               ApplicationEventPublisher applicationEventPublisher,
                               StompClient stompClient) {
        this.workerWalletAddress = workerConfigurationService.getWorkerWalletAddress();
        this.eventPublisher = applicationEventPublisher;
        this.stompClient = stompClient;
    }

    /**
     * Subscribe to a topic and handle {@link TaskNotification}.
     * 
     * @param chainTaskId
     */
    public synchronized void subscribeToTopic(String chainTaskId) {
        String topic = getTaskTopicName(chainTaskId);
        if (this.chainTaskIdToSubscription.containsKey(chainTaskId)) {
            log.info("Already subscribed to topic [chainTaskId:{}, topic:{}]",
                    chainTaskId, topic);
            return;
        }
        MessageHandler messageHandler = new MessageHandler(chainTaskId, this.workerWalletAddress);
        Subscription subscription = stompClient.subscribeToTopic(topic, messageHandler);
        this.chainTaskIdToSubscription.put(chainTaskId, subscription);
        log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, topic);
    }

    /**
     * Unsubscribe from topic if already subscribed.
     * 
     * @param chainTaskId
     */
    public void unsubscribeFromTopic(String chainTaskId) {
        if (!isSubscribedToTopic(chainTaskId)) {
            log.error("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
            return;
        }
        this.chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
        this.chainTaskIdToSubscription.remove(chainTaskId);
        log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
    }

    public boolean isSubscribedToTopic(String chainTaskId) {
        return this.chainTaskIdToSubscription.containsKey(chainTaskId);
    }

    /**
     * Update existing subscriptions if a new
     * STOMP session is created.
     */
    @EventListener(SessionCreatedEvent.class)
    private synchronized void reSubscribeToTopics() {
        log.debug("Received new SessionCreatedEvent");
        Set<String> chainTaskIds = this.chainTaskIdToSubscription.keySet();
        log.info("ReSubscribing to topics [chainTaskIds: {}]", chainTaskIds);
        chainTaskIds.forEach((chainTaskId) -> {
            this.chainTaskIdToSubscription.remove(chainTaskId);
            subscribeToTopic(chainTaskId);    
        });
        log.info("ReSubscribed to topics [chainTaskIds: {}]", chainTaskIds);
    }

    private String getTaskTopicName(String chainTaskId) {
        return "/topic/task/" + chainTaskId;
    }

    @AllArgsConstructor
    public class MessageHandler implements StompFrameHandler {

        private final String chainTaskId;
        private final String workerWalletAddress;

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return TaskNotification.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, @Nullable Object payload) {
            if (payload == null) {
                log.error("Payload of TaskNotification is null [chainTaskId:{}]", this.chainTaskId);
                return;
            }
            TaskNotification taskNotification = (TaskNotification) payload;
            if (!isWorkerInvolved(taskNotification)) {
                return;
            }
            log.info("PubSub service received new TaskNotification [chainTaskId:{}, type:{}]",
                    this.chainTaskId, taskNotification.getTaskNotificationType());
            eventPublisher.publishEvent(taskNotification);
        }

        private boolean isWorkerInvolved(TaskNotification notification) {
            return notification.getWorkersAddress() != null &&
                    (notification.getWorkersAddress().isEmpty() || // for all workers
                    notification.getWorkersAddress().contains(this.workerWalletAddress));
        }    
    }
}