/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.core.notification.TaskNotification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class SubscriptionService implements Purgeable {

    private final Map<String, Subscription> chainTaskIdToSubscription = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private final StompClientService stompClientService;
    private final String workerWalletAddress;

    private final Lock sessionLock = new ReentrantLock();
    private final Condition sessionReadyCondition = sessionLock.newCondition();
    @Getter
    private boolean sessionReady = false;

    public SubscriptionService(ApplicationEventPublisher applicationEventPublisher,
                               StompClientService stompClientService,
                               String workerWalletAddress) {
        this.eventPublisher = applicationEventPublisher;
        this.stompClientService = stompClientService;
        this.workerWalletAddress = workerWalletAddress;
    }

    /**
     * Subscribe to a task's topic and handle {@link TaskNotification}.
     *
     * @param chainTaskId id of the task to which to subscribe
     */
    public void subscribeToTopic(String chainTaskId) {
        String topic = getTaskTopicName(chainTaskId);
        if (this.chainTaskIdToSubscription.containsKey(chainTaskId)) {
            log.info("Already subscribed to topic [chainTaskId:{}, topic:{}]",
                    chainTaskId, topic);
            return;
        }
        MessageHandler messageHandler = new MessageHandler(chainTaskId, this.workerWalletAddress);
        stompClientService.subscribeToTopic(topic, messageHandler).ifPresentOrElse(
                subscription -> {
                    this.chainTaskIdToSubscription.put(chainTaskId, subscription);
                    log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, topic);
                },
                () -> log.error("Topic subscription failed [chainTaskId:{}, topic:{}]", chainTaskId, topic)
        );
    }

    /**
     * Unsubscribe from topic if already subscribed.
     *
     * @param chainTaskId id of the task to unsubscribe and purge
     * @return true if unsubscribed and purge topic successfully, false otherwise
     */
    @Override
    public boolean purgeTask(String chainTaskId) {
        if (!isSubscribedToTopic(chainTaskId)) {
            log.error("Already unsubscribed from topic [chainTaskId:{}]", chainTaskId);
            return true;
        }
        this.chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
        this.chainTaskIdToSubscription.remove(chainTaskId);
        log.info("Unsubscribed from topic [chainTaskId:{}]", chainTaskId);
        return !isSubscribedToTopic(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        final List<String> tasksIds = new ArrayList<>(chainTaskIdToSubscription.keySet());
        tasksIds.forEach(this::purgeTask);
    }

    /**
     * Check if the worker is subscribed to a task's topic.
     *
     * @param chainTaskId id of the task to check
     * @return true if subscribed, false otherwise
     */
    public boolean isSubscribedToTopic(String chainTaskId) {
        return this.chainTaskIdToSubscription.containsKey(chainTaskId);
    }

    /**
     * Update existing subscriptions if a new
     * STOMP session is created.
     */
    @EventListener(SessionCreatedEvent.class)
    synchronized void reSubscribeToTopics() {
        log.debug("Received new SessionCreatedEvent");
        Set<String> chainTaskIds = this.chainTaskIdToSubscription.keySet();
        if (chainTaskIds.isEmpty()) {
            log.info("No topic to resubscribe to");
        } else {
            log.info("ReSubscribing to topics [chainTaskIds: {}]", chainTaskIds);
            chainTaskIds.forEach(chainTaskId -> {
                this.chainTaskIdToSubscription.remove(chainTaskId);
                subscribeToTopic(chainTaskId);
            });

            log.info("ReSubscribed to topics [chainTaskIds: {}]", chainTaskIds);
        }

        if (sessionReady) {
            log.warn("STOMP session was already up before receiving this event");
        }

        sessionReady = true;
        sessionLock.lock();
        try {
            sessionReadyCondition.signalAll();
        } finally {
            sessionLock.unlock();
        }
    }

    @EventListener(SessionLostEvent.class)
    synchronized void sessionLost() {
        log.warn("STOMP session is down, blocking updates until it is restored.");
        if (!sessionReady) {
            log.warn("STOMP session was already down before receiving this event");
        }
        sessionReady = false;
    }

    /**
     * Wait for the session to be ready.
     * Useful to prevent actions to execute while the STOMP session is disconnected.
     *
     * @throws InterruptedException if the current thread is interrupted
     *                              while waiting.
     */
    public void waitForSessionReady() throws InterruptedException {
        while (!sessionReady) {
            sessionLock.lock();
            try {
                sessionReadyCondition.await();
            } finally {
                sessionLock.unlock();
            }
        }
    }

    private String getTaskTopicName(String chainTaskId) {
        return "/topic/task/" + chainTaskId;
    }

    /**
     * An implementation of {@link StompFrameHandler} that
     * handles received task notifications.
     */
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
