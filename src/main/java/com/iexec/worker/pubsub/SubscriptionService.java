package com.iexec.worker.pubsub;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SubscriptionService extends StompSessionHandlerAdapter {

    private final String coreHost;
    private final int corePort;
    private final String workerWalletAddress;
    // external services
    private CoreTaskClient coreTaskClient;
    private ResultRepoClient resultRepoClient;
    private ResultService resultService;
    private RevealService revealService;
    // internal components
    private StompSession session;
    private Map<String, StompSession.Subscription> chainTaskIdToSubscription;

    public SubscriptionService(CoreConfigurationService coreConfigurationService,
                               WorkerConfigurationService workerConfigurationService,
                               CoreTaskClient coreTaskClient,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService,
                               RevealService revealService) {
        this.coreTaskClient = coreTaskClient;
        this.resultRepoClient = resultRepoClient;
        this.resultService = resultService;
        this.revealService = revealService;

        this.coreHost = coreConfigurationService.getHost();
        this.corePort = coreConfigurationService.getPort();
        this.workerWalletAddress = workerConfigurationService.getWorkerWalletAddress();

        chainTaskIdToSubscription = new ConcurrentHashMap<>();
    }

    @PostConstruct
    private void run() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        String url = "ws://" + coreHost + ":" + corePort + "/connect";
        stompClient.connect(url, this);
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("SubscriptionService set up [session: {}]", session.getSessionId());
        this.session = session;
    }

    public void subscribeToTaskNotifications(String chainTaskId) {
        if (chainTaskIdToSubscription.containsKey(chainTaskId)) {
            log.info("Already subscribed to TaskNotification [chainTaskId:{}]", chainTaskId);
            return;
        }
        StompSession.Subscription subscription = session.subscribe(getTaskTopicName(chainTaskId), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskNotification.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                TaskNotification taskNotification = (TaskNotification) payload;
                handleTaskNotification(taskNotification);
            }
        });
        chainTaskIdToSubscription.put(chainTaskId, subscription);
        log.info("Subscribed to topic [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
    }

    private void handleTaskNotification(TaskNotification notif) {
        if (notif.getWorkerAddress().equals(workerWalletAddress)
                || notif.getWorkerAddress().isEmpty()) {
            log.info("Received notification [notification:{}]", notif);

            TaskNotificationType type = notif.getTaskNotificationType();
            String chainTaskId = notif.getChainTaskId();

            switch (type) {
                case PLEASE_REVEAL:
                    reveal(chainTaskId);
                    break;

                case UPLOAD:
                    uploadResult(chainTaskId);
                    break;

                case COMPLETED:
                    completeTask(chainTaskId);
                    break;

                default:
                    break;
            }
        }
    }

    private void reveal(String chainTaskId) {
        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        if (!revealService.canReveal(chainTaskId)) {
            log.warn("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
        }

        try {
            revealService.reveal(chainTaskId);
            log.info("The worker has revealed [chainTaskId:{}]", chainTaskId);
            log.info("Update replicate status [status:{}]", ReplicateStatus.REVEALED);
            coreTaskClient.updateReplicateStatus(chainTaskId, workerWalletAddress, ReplicateStatus.REVEALED);
        } catch (Exception e) {
            log.error("An error has occurred while revealing [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }
    }

    private void uploadResult(String chainTaskId) {
        log.info("Update replicate status [status:{}]", ReplicateStatus.UPLOADING_RESULT);
        coreTaskClient.updateReplicateStatus(chainTaskId, workerWalletAddress, ReplicateStatus.UPLOADING_RESULT);

        resultRepoClient.addResult(resultService.getResultModelWithZip(chainTaskId));

        log.info("Update replicate status [status:{}]", ReplicateStatus.RESULT_UPLOADED);
        coreTaskClient.updateReplicateStatus(chainTaskId, workerWalletAddress, ReplicateStatus.RESULT_UPLOADED);
    }

    private void completeTask(String chainTaskId) {
        // unsubscribe from the topic and remove the associated result from the machine
        unsubscribeFromTaskNotifications(chainTaskId);
        resultService.removeResult(chainTaskId);
        //log.info("Update replicate status [status:{}]", ReplicateStatus.COMPLETED);
        //coreTaskClient.updateReplicateStatus(chainTaskId, workerWalletAddress, COMPLETED);
    }

    private void unsubscribeFromTaskNotifications(String chainTaskId) {
        if (!chainTaskIdToSubscription.containsKey(chainTaskId)) {
            log.info("Already unsubscribed from TaskNotification [chainTaskId:{}]", chainTaskId);
            return;
        }
        chainTaskIdToSubscription.get(chainTaskId).unsubscribe();
        chainTaskIdToSubscription.remove(chainTaskId);
        log.info("Unsubscribed from taskNotification [chainTaskId:{}, topic:{}]", chainTaskId, getTaskTopicName(chainTaskId));
    }

    private String getTaskTopicName(String taskId) {
        return "/topic/task/" + taskId;
    }
}