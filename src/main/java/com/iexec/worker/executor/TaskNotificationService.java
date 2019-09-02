package com.iexec.worker.executor;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.common.replicate.ReplicateStatusUpdate.*;


@Slf4j
@Service
public class TaskNotificationService {

    private TaskManagerService taskManagerService;
    private CustomCoreFeignClient customCoreFeignClient;
    private ApplicationEventPublisher applicationEventPublisher;
    private SubscriptionService subscriptionService;
    private ContributionService contributionService;


    public TaskNotificationService(TaskManagerService taskManagerService,
                                   CustomCoreFeignClient customCoreFeignClient,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   SubscriptionService subscriptionService,
                                   ContributionService contributionService
                                   ) {
        this.taskManagerService = taskManagerService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.subscriptionService = subscriptionService;
        this.contributionService = contributionService;
    }


    /**
     * Note to dev: In spring the code executed in an @EventListener method will be in the same thread than the
     * method that triggered the event. We don't want this to be the case here so this method should be Async.
     */
    @EventListener
    @Async
    protected void onTaskNotification(TaskNotification notification) {
        String chainTaskId = notification.getChainTaskId();
        TaskNotificationType action = notification.getTaskNotificationType();
        ReplicateStatusUpdate statusUpdate = null;
        TaskNotificationType nextAction = null;
        log.info("Received TaskEvent [chainTaskId:{}, action:{}]", chainTaskId, action);

        if (action == null) {
            log.error("No action to do [chainTaskId:{}]", chainTaskId);
            return;
        }

        TaskNotificationExtra extra = notification.getTaskNotificationExtra();

        if (!storeContributionAuthorizationFromExtraIfPresent(extra)){
            log.error("Should storeContributionAuthorizationFromExtraIfPresent [chainTaskId:{}]", chainTaskId);
            return;
        }

        switch (action) {
            case PLEASE_START:
                updateStatusAndGetNextAction(chainTaskId, STARTING);
                statusUpdate = taskManagerService.start(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_DOWNLOAD_APP:
                updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADING);
                statusUpdate = taskManagerService.downloadApp(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_DOWNLOAD_DATA:
                updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADING);
                statusUpdate = taskManagerService.downloadData(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_COMPUTE:
                updateStatusAndGetNextAction(chainTaskId, COMPUTING);
                statusUpdate = taskManagerService.compute(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_CONTRIBUTE:
                updateStatusAndGetNextAction(chainTaskId, CONTRIBUTING);
                statusUpdate = taskManagerService.contribute(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_REVEAL:
                updateStatusAndGetNextAction(chainTaskId, REVEALING);
                statusUpdate = taskManagerService.reveal(chainTaskId, extra);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_UPLOAD:
                updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADING);
                statusUpdate = taskManagerService.uploadResult(chainTaskId);
                nextAction = updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_COMPLETE:
                updateStatusAndGetNextAction(chainTaskId, COMPLETING);
                statusUpdate = taskManagerService.complete(chainTaskId);
                updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            //TODO merge abort
            case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                boolean isAborted = taskManagerService.abort(chainTaskId);
                if (!isAborted) {
                    return;
                }
                updateStatusAndGetNextAction(chainTaskId, ABORTED, CONTRIBUTION_TIMEOUT);
                break;
            case PLEASE_ABORT_CONSENSUS_REACHED:
                boolean isAbortedAfterConsensusReached = taskManagerService.abort(chainTaskId);
                if (!isAbortedAfterConsensusReached) {
                    return;
                }
                updateStatusAndGetNextAction(chainTaskId, ABORTED, CONSENSUS_REACHED);
                break;
            default:
                break;
        }

        subscriptionService.handleSubscription(notification);
        if (nextAction != null){
            log.info("Sending next action [chainTaskId:{}, nextAction:{}]", chainTaskId, nextAction);
            applicationEventPublisher.publishEvent(TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .taskNotificationType(nextAction)
                    .build()
            );
        } else {
            log.warn("No more actions to do [chainTaskId:{}]", chainTaskId);
        }

    }

    private boolean storeContributionAuthorizationFromExtraIfPresent(TaskNotificationExtra extra) {
        if (extra != null && extra.getContributionAuthorization() != null){
            return contributionService.putContributionAuthorization(extra.getContributionAuthorization());
        }
        return true;
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                              ReplicateStatus status) {
        ReplicateStatusUpdate statusUpdate = workerRequest(status);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                              ReplicateStatus status,
                                                              ReplicateStatusCause cause) {
        ReplicateStatusDetails details = ReplicateStatusDetails.builder().cause(cause).build();
        ReplicateStatusUpdate statusUpdate = workerRequest(status, details);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatusUpdate statusUpdate) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]",
                chainTaskId, statusUpdate.getStatus(), statusUpdate.getDetails());

        TaskNotificationType next = customCoreFeignClient.updateReplicateStatus(chainTaskId, statusUpdate);

        log.info("update replicate response [chainTaskId:{}, status:{}, next:{}]",
                chainTaskId, statusUpdate.getStatus(), next);
        return next;
    }
}