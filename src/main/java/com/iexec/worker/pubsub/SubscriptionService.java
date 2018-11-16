package com.iexec.worker.pubsub;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.utils.CoreConfigurationService;
import com.iexec.worker.utils.WorkerConfigurationService;
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

    private CoreConfigurationService coreConfigurationService;
    private WorkerConfigurationService workerConfigurationService;
    private CoreTaskClient coreTaskClient;
    private ResultRepoClient resultRepoClient;
    private ResultService resultService;
    private StompSession session;
    private Map<String, StompSession.Subscription> taskIdToSubscription;

    public SubscriptionService(CoreConfigurationService coreConfigurationService,
                               WorkerConfigurationService workerConfigurationService,
                               CoreTaskClient coreTaskClient,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService) {
        this.coreConfigurationService = coreConfigurationService;
        this.workerConfigurationService = workerConfigurationService;
        this.coreTaskClient = coreTaskClient;
        this.resultRepoClient = resultRepoClient;
        this.resultService = resultService;
        taskIdToSubscription = new ConcurrentHashMap<>();
    }

    @PostConstruct
    private void run() {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        String url = "ws://"
                + coreConfigurationService.getHost()
                + ":"
                + coreConfigurationService.getPort()
                + "/connect";
        stompClient.connect(url, this);
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("SubscriptionService set up [session: {}]", session.getSessionId());
        this.session = session;
    }

    public void subscribeToTaskNotifications(String taskId) {
        if (taskIdToSubscription.containsKey(taskId)) {
            log.info("Already subscribed to TaskNotification [taskId:{}]", taskId);
            return;
        }
        StompSession.Subscription subscription = session.subscribe(getTaskTopicName(taskId), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskNotification.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                TaskNotification taskNotification = (TaskNotification) payload;
                handleTaskNotification(taskNotification, taskId);

            }
        });
        taskIdToSubscription.put(taskId, subscription);
        log.info("Subscribed to {}", getTaskTopicName(taskId));
    }

    private void handleTaskNotification(TaskNotification taskNotification, String taskId) {
        if (taskNotification.getWorkerAddress().equals(workerConfigurationService.getWorkerWalletAddress())
                || taskNotification.getWorkerAddress().isEmpty()) {
            log.info("Received [{}]", taskNotification);

            if (taskNotification.getTaskNotificationType().equals(TaskNotificationType.UPLOAD)) {

                log.info("Update replicate status [status:{}]", ReplicateStatus.UPLOADING_RESULT);
                coreTaskClient.updateReplicateStatus(taskNotification.getChainTaskId(),
                        workerConfigurationService.getWorkerWalletAddress(),
                        ReplicateStatus.UPLOADING_RESULT);

                //Upload result cause core is asking for
                resultRepoClient.addResult(resultService.getResultModelWithZip(taskNotification.getChainTaskId()));
                log.info("Update replicate status [status:{}]", ReplicateStatus.RESULT_UPLOADED);
                coreTaskClient.updateReplicateStatus(taskNotification.getChainTaskId(),
                        workerConfigurationService.getWorkerWalletAddress(),
                        ReplicateStatus.RESULT_UPLOADED);
            } else if (taskNotification.getTaskNotificationType().equals(TaskNotificationType.COMPLETED)) {
                unsubscribeFromTaskNotifications(taskId);
            }
        }
    }

    public void unsubscribeFromTaskNotifications(String taskId) {
        if (!taskIdToSubscription.containsKey(taskId)) {
            log.info("Already unsubscribed to TaskNotification [taskId:{}]", taskId);
            return;
        }
        taskIdToSubscription.get(taskId).unsubscribe();
        taskIdToSubscription.remove(taskId);
        log.info("Unsubscribed from {}", getTaskTopicName(taskId));
    }

    private String getTaskTopicName(String taskId) {
        return "/topic/task/" + taskId;
    }
}