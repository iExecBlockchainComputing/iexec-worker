package com.iexec.worker.amnesia;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.replicate.ReplicateService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/*
 * This service is used to remind the worker of possible interrupted works
 * after a restart and how to deal with each interruption
 */
@Slf4j
@Service
public class AmnesiaRecoveryService {

    private CustomFeignClient customFeignClient;
    private SubscriptionService subscriptionService;
    private ReplicateService replicateService;
    private ResultService resultService;
    private TaskExecutorService taskExecutorService;
    private IexecHubService iexecHubService;

    public AmnesiaRecoveryService(CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ReplicateService replicateService,
                                  ResultService resultService,
                                  TaskExecutorService taskExecutorService,
                                  IexecHubService iexecHubService) {
        this.customFeignClient = customFeignClient;
        this.subscriptionService = subscriptionService;
        this.replicateService = replicateService;
        this.resultService = resultService;
        this.taskExecutorService = taskExecutorService;
        this.iexecHubService = iexecHubService;
    }

    public List<String> recoverInterruptedReplicates() {
        long lasAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        List<TaskNotification> missedTaskNotifications = customFeignClient.getMissedTaskNotifications(
                lasAvailableBlockNumber);
        List<String> recoveredChainTaskIds = new ArrayList<>();

        if (missedTaskNotifications == null || missedTaskNotifications.isEmpty()) {
            log.info("No interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (TaskNotification missedTaskNotification : missedTaskNotifications) {

            ContributionAuthorization contributionAuth = missedTaskNotification.getTaskNotificationExtra().getContributionAuthorization();
            TaskNotificationType taskNotificationType = missedTaskNotification.getTaskNotificationType();
            String chainTaskId = contributionAuth.getChainTaskId();
            boolean isResultAvailable = resultService.isResultAvailable(chainTaskId);

            log.info("Recovering interrupted task [chainTaskId:{}, taskNotificationType:{}]",
                    chainTaskId, taskNotificationType);

            if (!isResultAvailable && taskNotificationType != TaskNotificationType.PLEASE_CONTRIBUTE) {
                log.error("Could not recover task, result not found [chainTaskId:{}, RecoveryAction:{}]",
                        chainTaskId, taskNotificationType);
                continue;
            }

            Optional<AvailableReplicateModel> oReplicateModel =
                    replicateService.retrieveAvailableReplicateModelFromContribAuth(contributionAuth);

            if (!oReplicateModel.isPresent()) {
                log.error("Could not recover task, no replicateModel retrieved [chainTaskId:{}, RecoveryAction:{}]",
                        chainTaskId, taskNotificationType);
                continue;
            }

            AvailableReplicateModel replicateModel = oReplicateModel.get();
            recoverReplicate(missedTaskNotification, replicateModel);
            recoveredChainTaskIds.add(chainTaskId);
        }

        return recoveredChainTaskIds;
    }

    public void recoverReplicate(TaskNotification taskNotification,
                                 AvailableReplicateModel replicateModel) {
        String chainTaskId = taskNotification.getChainTaskId();

        subscriptionService.subscribeToTopic(chainTaskId);
        resultService.saveResultInfo(chainTaskId, replicateModel);
        subscriptionService.handleTaskNotification(taskNotification);
    }

}