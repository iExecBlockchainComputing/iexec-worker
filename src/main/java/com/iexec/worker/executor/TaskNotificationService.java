package com.iexec.worker.executor;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.iexec.common.replicate.ReplicateStatus.*;


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
                boolean isStarted = taskManagerService.start(chainTaskId);
                if (!isStarted) {
                    updateStatusAndGetNextAction(chainTaskId, START_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, STARTED);
                break;
            case PLEASE_DOWNLOAD_APP:
                updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADING);
                boolean isAppDownloaded = taskManagerService.downloadApp(chainTaskId);
                if (!isAppDownloaded) {
                    updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOAD_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADED);
                break;
            case PLEASE_DOWNLOAD_DATA:
                updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADING);
                boolean isDataDownloaded = taskManagerService.downloadData(chainTaskId);
                if (!isDataDownloaded) {
                    updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOAD_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADED);
                break;
            case PLEASE_COMPUTE:
                updateStatusAndGetNextAction(chainTaskId, COMPUTING);
                boolean isComputed = taskManagerService.compute(chainTaskId);
                if (!isComputed) {
                    updateStatusAndGetNextAction(chainTaskId, COMPUTE_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, COMPUTED);
                break;
            case PLEASE_CONTRIBUTE:
                updateStatusAndGetNextAction(chainTaskId, CONTRIBUTING);
                boolean isContributed = taskManagerService.contribute(chainTaskId);
                if (!isContributed) {
                    updateStatusAndGetNextAction(chainTaskId, CONTRIBUTE_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, CONTRIBUTED);
                break;
            case PLEASE_REVEAL:
                updateStatusAndGetNextAction(chainTaskId, REVEALING);
                boolean isRevealed = taskManagerService.reveal(chainTaskId, extra);
                if (!isRevealed) {
                    updateStatusAndGetNextAction(chainTaskId, REVEAL_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, REVEALED);
                break;
            case PLEASE_UPLOAD:
                updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADING);
                ReplicateDetails uploadDetails = taskManagerService.uploadResult(chainTaskId);
                if (uploadDetails == null) {
                    updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOAD_FAILED);
                    return;
                }
                nextAction = updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADED, uploadDetails);
                break;
            case PLEASE_COMPLETE:
                updateStatusAndGetNextAction(chainTaskId, COMPLETING);
                boolean isCompleted = taskManagerService.complete(chainTaskId);
                if (!isCompleted) {
                    updateStatusAndGetNextAction(chainTaskId, COMPLETE_FAILED);
                    return;
                }
                updateStatusAndGetNextAction(chainTaskId, COMPLETED);
                break;
            //TODO merge abort
            case PLEASE_ABORT_CONTRIBUTION_TIMEOUT:
                boolean isAborted = taskManagerService.abort(chainTaskId);
                if (!isAborted) {
                    return;
                }
                updateStatusAndGetNextAction(chainTaskId, ABORTED_ON_CONTRIBUTION_TIMEOUT);
                break;
            case PLEASE_ABORT_CONSENSUS_REACHED:
                boolean isAbortedAfterConsensusReached = taskManagerService.abort(chainTaskId);
                if (!isAbortedAfterConsensusReached) {
                    return;
                }
                updateStatusAndGetNextAction(chainTaskId, ABORTED_ON_CONSENSUS_REACHED);
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

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatus status, ReplicateDetails details) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]", chainTaskId, status, details);
        if (details == null){
            details = ReplicateDetails.builder().build();
        }
        TaskNotificationType next = customCoreFeignClient.updateReplicateStatus(chainTaskId, status, details);
        log.info("update replicate response [chainTaskId:{}, status:{}, next:{}]", chainTaskId, status, next);
        return next;
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatus status) {
        return updateStatusAndGetNextAction(chainTaskId, status, null);
    }
}