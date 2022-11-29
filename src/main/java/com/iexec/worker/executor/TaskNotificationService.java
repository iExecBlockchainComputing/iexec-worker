/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.executor;

import com.iexec.common.chain.ChainTask;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.*;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_DESCRIPTION_NOT_FOUND;


@Slf4j
@Service
public class TaskNotificationService {

    private final TaskManagerService taskManagerService;
    private final CustomCoreFeignClient customCoreFeignClient;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SubscriptionService subscriptionService;
    private final ContributionService contributionService;
    private final IexecHubService iexecHubService;

    private final Map<String, Long> finalDeadlineForTask = ExpiringMap
            .builder()
            .expiration(1, TimeUnit.HOURS)
            .build();

    public TaskNotificationService(
            TaskManagerService taskManagerService,
            CustomCoreFeignClient customCoreFeignClient,
            ApplicationEventPublisher applicationEventPublisher,
            SubscriptionService subscriptionService,
            ContributionService contributionService,
            IexecHubService iexecHubService) {
        this.taskManagerService = taskManagerService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.subscriptionService = subscriptionService;
        this.contributionService = contributionService;
        this.iexecHubService = iexecHubService;
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
        ReplicateActionResponse actionResponse;
        TaskNotificationType nextAction = null;
        log.debug("Received TaskNotification [chainTaskId:{}, action:{}]", chainTaskId, action);

        if (action == null) {
            log.error("No action to do [chainTaskId:{}]", chainTaskId);
            return;
        }

        TaskNotificationExtra extra = notification.getTaskNotificationExtra();

        if (!storeWorkerpoolAuthorizationFromExtraIfPresent(extra)){
            log.error("Should storeWorkerpoolAuthorizationFromExtraIfPresent [chainTaskId:{}]", chainTaskId);
            return;
        }
        // TODO use taskDescription as arg for all methods
        // and don't fetch it in each method.
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Failed to get task description [chainTaskId:{}]", chainTaskId);
            taskManagerService.abort(chainTaskId);
            updateStatusAndGetNextAction(chainTaskId, ABORTED, TASK_DESCRIPTION_NOT_FOUND);
            return;
        }
        switch (action) {
            case PLEASE_START:
                updateStatusAndGetNextAction(chainTaskId, STARTING);
                actionResponse = taskManagerService.start(chainTaskId);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, STARTED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, START_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_DOWNLOAD_APP:
                updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADING);
                actionResponse = taskManagerService.downloadApp(chainTaskId);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOAD_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_DOWNLOAD_DATA:
                updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADING);
                actionResponse = taskManagerService.downloadData(taskDescription);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOAD_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_COMPUTE:
                updateStatusAndGetNextAction(chainTaskId, COMPUTING);
                actionResponse = taskManagerService.compute(chainTaskId);
                if (actionResponse.getDetails() != null) {
                    actionResponse.getDetails().tailLogs();
                }
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, COMPUTED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, COMPUTE_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_CONTRIBUTE:
                updateStatusAndGetNextAction(chainTaskId, CONTRIBUTING);
                actionResponse = taskManagerService.contribute(chainTaskId);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, CONTRIBUTED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, CONTRIBUTE_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_REVEAL:
                updateStatusAndGetNextAction(chainTaskId, REVEALING);
                actionResponse = taskManagerService.reveal(chainTaskId, extra);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, REVEALED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, REVEAL_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_UPLOAD:
                updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADING);
                actionResponse = taskManagerService.uploadResult(chainTaskId);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOAD_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_COMPLETE:
                updateStatusAndGetNextAction(chainTaskId, COMPLETING);
                actionResponse = taskManagerService.complete(chainTaskId);
                subscriptionService.unsubscribeFromTopic(chainTaskId);
                if (actionResponse.isSuccess()) {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, COMPLETED, actionResponse.getDetails());
                } else {
                    nextAction = updateStatusAndGetNextAction(chainTaskId, COMPLETE_FAILED, actionResponse.getDetails());
                }
                break;
            case PLEASE_ABORT:
                if (!taskManagerService.abort(chainTaskId)) {
                    log.error("Failed to abort task [chainTaskId:{}]", chainTaskId);
                    return;
                }
                TaskAbortCause taskAbortCause = notification.getTaskAbortCause();
                ReplicateStatusCause replicateAbortCause = ReplicateStatusCause.getReplicateAbortCause(taskAbortCause);
                updateStatusAndGetNextAction(chainTaskId, ABORTED, replicateAbortCause);
                break;
            default:
                break;
        }

        if (nextAction != null) {
            log.debug("Sending next action [chainTaskId:{}, nextAction:{}]", chainTaskId, nextAction);
            applicationEventPublisher.publishEvent(TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .taskNotificationType(nextAction)
                    .build()
            );
        } else {
            log.warn("No more actions to do [chainTaskId:{}]", chainTaskId);
        }

    }

    private boolean storeWorkerpoolAuthorizationFromExtraIfPresent(TaskNotificationExtra extra) {
        if (extra != null && extra.getWorkerpoolAuthorization() != null){
            return contributionService.putWorkerpoolAuthorization(extra.getWorkerpoolAuthorization());
        }
        return true;
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                              ReplicateStatus status) {
        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(status);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                              ReplicateStatus status,
                                                              ReplicateStatusCause cause) {
        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(status, cause);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                              ReplicateStatus status,
                                                              ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .status(status)
                .details(details)
                .build();

        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatusUpdate statusUpdate) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]",
                chainTaskId, statusUpdate.getStatus(), statusUpdate.getDetailsWithoutLogs());

        TaskNotificationType next = null;
        // As long as the Core doesn't reply, we try to contact it. It may be rebooting.
        while (next == null && !isFinalDeadlineReached(chainTaskId, Instant.now().toEpochMilli())) {
            next = customCoreFeignClient.updateReplicateStatus(chainTaskId, statusUpdate);
        }

        log.info("update replicate response [chainTaskId:{}, status:{}, next:{}]",
                chainTaskId, statusUpdate.getStatus(), next);
        return next;
    }

    /**
     * Checks whether the final deadline is reached.
     * If never called before for this task, retrieves the final deadline from the chain and caches it.
     * If already called, then retrieves the final deadline from the cache.
     * <p>
     * Note that if the task is unknown on the chain, then the final deadline is considered as reached.
     *
     * @param chainTaskId Task ID whose final deadline should be checked.
     * @param now         Time to check final deadline against.
     * @return {@literal true} if the final deadline is met or the task is unknown on-chain,
     * {@literal false} otherwise.
     */
    boolean isFinalDeadlineReached(String chainTaskId, long now) {
        final Long finalDeadline;

        if (finalDeadlineForTask.containsKey(chainTaskId)) {
            finalDeadline = finalDeadlineForTask.get(chainTaskId);
        } else {
            final Optional<ChainTask> oTask = iexecHubService.getChainTask(chainTaskId);
            if (oTask.isEmpty()) {
                return true;
            }

            finalDeadline = oTask.get().getFinalDeadline();
            finalDeadlineForTask.put(chainTaskId, finalDeadline);
        }

        return now >= finalDeadline;
    }
}
