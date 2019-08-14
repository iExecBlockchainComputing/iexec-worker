package com.iexec.worker.replicate;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/*
 * This service is used to remind the worker of possible interrupted works
 * after a restart and how to deal with each interruption.
 */
@Slf4j
@Service
public class ReplicateRecoveryService {

    private CustomFeignClient customFeignClient;
    private SubscriptionService subscriptionService;
    private ResultService resultService;
    private IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;

    public ReplicateRecoveryService(CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ResultService resultService,
                                  IexecHubService iexecHubService,
                                  ApplicationEventPublisher applicationEventPublisher) {
        this.customFeignClient = customFeignClient;
        this.subscriptionService = subscriptionService;
        this.resultService = resultService;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    //TODO clean that
    public List<String> recoverInterruptedReplicates() {
        long latestAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        List<TaskNotification> missedTaskNotifications = customFeignClient.getMissedTaskNotifications(
                latestAvailableBlockNumber);
        List<String> recoveredChainTaskIds = new ArrayList<>();

        if (missedTaskNotifications == null || missedTaskNotifications.isEmpty()) {
            log.info("No interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (TaskNotification missedTaskNotification : missedTaskNotifications) {
            TaskNotificationType taskNotificationType = missedTaskNotification.getTaskNotificationType();
            String chainTaskId = missedTaskNotification.getChainTaskId();
            boolean isResultAvailable = resultService.isResultAvailable(chainTaskId);

            log.info("Recovering interrupted task [chainTaskId:{}, taskNotificationType:{}]",
                    chainTaskId, taskNotificationType);

            if (!isResultAvailable && taskNotificationType != TaskNotificationType.PLEASE_CONTRIBUTE) {
                log.error("Could not recover task, result not found [chainTaskId:{}, taskNotificationType:{}]",
                        chainTaskId, taskNotificationType);
                continue;
            }

            Optional<TaskDescription> optionalTaskDescription = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

            if (!optionalTaskDescription.isPresent()) {
                log.error("Could not recover task, no TaskDescription retrieved [chainTaskId:{}, taskNotificationType:{}]",
                        chainTaskId, taskNotificationType);
                continue;
            }

            TaskDescription taskDescription = optionalTaskDescription.get();

            resultService.saveResultInfo(chainTaskId, taskDescription);

            subscriptionService.subscribeToTopic(chainTaskId);
            applicationEventPublisher.publishEvent(missedTaskNotification);

            recoveredChainTaskIds.add(chainTaskId);
        }

        return recoveredChainTaskIds;
    }
}