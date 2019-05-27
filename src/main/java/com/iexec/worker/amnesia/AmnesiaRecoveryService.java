package com.iexec.worker.amnesia;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
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
        List<InterruptedReplicateModel> interruptedReplicates = customFeignClient.getInterruptedReplicates(
                lasAvailableBlockNumber);
        List<String> recoveredChainTaskIds = new ArrayList<>();

        if (interruptedReplicates == null || interruptedReplicates.isEmpty()) {
            log.info("No interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (InterruptedReplicateModel interruptedReplicate : interruptedReplicates) {

            ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
            TaskNotificationType taskNotificationType = interruptedReplicate.getTaskNotificationType();
            String chainTaskId = contributionAuth.getChainTaskId();
            boolean isResultAvailable = isResultAvailable(chainTaskId);

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
            recoverReplicate(interruptedReplicate, replicateModel);
            recoveredChainTaskIds.add(chainTaskId);
        }

        return recoveredChainTaskIds;
    }

    public void recoverReplicate(InterruptedReplicateModel interruptedReplicate,
                                 AvailableReplicateModel replicateModel) {

        ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();

        subscriptionService.subscribeToTopic(chainTaskId);
        resultService.saveResultInfo(chainTaskId, replicateModel);

        TaskNotification taskNotification = null;

        switch (interruptedReplicate.getTaskNotificationType()) {
            case PLEASE_CONTRIBUTE:
                recoverReplicateByContributing(contributionAuth, replicateModel);
                break;
            case PLEASE_ABORT_CONSENSUS_REACHED:
                taskNotification = TaskNotification.builder()
                        .chainTaskId(chainTaskId)
                        .taskNotificationType(TaskNotificationType.PLEASE_ABORT_CONSENSUS_REACHED)
                        .build();
                break;
            case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                taskNotification = TaskNotification.builder()
                        .chainTaskId(chainTaskId)
                        .taskNotificationType(TaskNotificationType.PLEASE_ABORT_CONTRIBUTION_TIMEOUT)
                        .build();
                break;

            case PLEASE_REVEAL:
                taskNotification = TaskNotification.builder()
                        .chainTaskId(chainTaskId)
                        .taskNotificationType(TaskNotificationType.PLEASE_REVEAL)
                        .blockNumber(iexecHubService.getLatestBlockNumber())
                        .build();
                break;
            case PLEASE_UPLOAD:
                taskNotification = TaskNotification.builder()
                        .chainTaskId(chainTaskId)
                        .taskNotificationType(TaskNotificationType.PLEASE_UPLOAD)
                        .build();
                break;
            case PLEASE_COMPLETE:
                taskNotification = TaskNotification.builder()
                        .chainTaskId(chainTaskId)
                        .taskNotificationType(TaskNotificationType.PLEASE_COMPLETE)
                        .build();
                break;
            default:
                break;
        }

        if (taskNotification != null) {
            subscriptionService.handleTaskNotification(taskNotification);
        }


    }

    private boolean isResultAvailable(String chainTaskId) {
        boolean isResultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean isResultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!isResultZipFound && !isResultFolderFound) return false;

        if (!isResultZipFound) resultService.zipResultFolder(chainTaskId);

        return true;
    }

    public void recoverReplicateByContributing(ContributionAuthorization contributionAuth,
                                               AvailableReplicateModel replicateModel) {

        String chainTaskId = contributionAuth.getChainTaskId();
        boolean isResultAvailable = isResultAvailable(chainTaskId);

        if (!isResultAvailable) {
            log.info("Result not found, re-running computation to recover task " +
                    "[chainTaskId:{}, recoveryAction:CONTRIBUTE]", chainTaskId);
            taskExecutorService.addReplicate(replicateModel);
            return;
        }

        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.contribute(contributionAuth);
    }
}