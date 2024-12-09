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

package com.iexec.worker.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.notification.TaskNotification;
import com.iexec.core.notification.TaskNotificationExtra;
import com.iexec.core.notification.TaskNotificationType;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.replicate.ReplicateActionResponse;
import com.iexec.worker.sms.SmsService;
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
    private final WorkerpoolAuthorizationService workerpoolAuthorizationService;
    private final IexecHubService iexecHubService;
    private final SmsService smsService;

    private final Map<String, Long> finalDeadlineForTask = ExpiringMap
            .builder()
            .expiration(1, TimeUnit.HOURS)
            .build();

    public TaskNotificationService(
            TaskManagerService taskManagerService,
            CustomCoreFeignClient customCoreFeignClient,
            ApplicationEventPublisher applicationEventPublisher,
            SubscriptionService subscriptionService,
            WorkerpoolAuthorizationService workerpoolAuthorizationService,
            IexecHubService iexecHubService,
            SmsService smsService) {
        this.taskManagerService = taskManagerService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.subscriptionService = subscriptionService;
        this.workerpoolAuthorizationService = workerpoolAuthorizationService;
        this.iexecHubService = iexecHubService;
        this.smsService = smsService;
    }

    /**
     * Note to dev: In spring the code executed in an @EventListener method will be in the same thread as the
     * method that triggered the event. We don't want this to be the case here so this method should be Async.
     */
    @Async
    @EventListener
    public void onTaskNotification(TaskNotification notification) {
        String chainTaskId = notification.getChainTaskId();
        TaskNotificationType action = notification.getTaskNotificationType();
        ReplicateActionResponse actionResponse;
        ReplicateStatus nextStatus;
        TaskNotificationType nextAction = null;
        log.debug("Received TaskNotification [chainTaskId:{}, action:{}]", chainTaskId, action);

        if (action == null) {
            log.error("No action to do [chainTaskId:{}]", chainTaskId);
            return;
        }

        TaskNotificationExtra extra = notification.getTaskNotificationExtra();

        if (!storeWorkerpoolAuthAndSmsFromExtraIfPresent(extra)) {
            log.error("Should storeWorkerpoolAuthorizationFromExtraIfPresent [chainTaskId:{}]", chainTaskId);
            return;
        }
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
                actionResponse = taskManagerService.start(taskDescription);
                nextStatus = actionResponse.isSuccess() ? STARTED : START_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_DOWNLOAD_APP:
                updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADING);
                actionResponse = taskManagerService.downloadApp(taskDescription);
                nextStatus = actionResponse.isSuccess() ? APP_DOWNLOADED : APP_DOWNLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_DOWNLOAD_DATA:
                updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADING);
                actionResponse = taskManagerService.downloadData(taskDescription);
                nextStatus = actionResponse.isSuccess() ? DATA_DOWNLOADED : DATA_DOWNLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_COMPUTE:
                updateStatusAndGetNextAction(chainTaskId, COMPUTING);
                actionResponse = taskManagerService.compute(taskDescription);
                if (actionResponse.getDetails() != null) {
                    actionResponse.getDetails().tailLogs();
                }
                nextStatus = actionResponse.isSuccess() ? COMPUTED : COMPUTE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_CONTRIBUTE:
                updateStatusAndGetNextAction(chainTaskId, CONTRIBUTING);
                actionResponse = taskManagerService.contribute(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? CONTRIBUTED : CONTRIBUTE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_REVEAL:
                updateStatusAndGetNextAction(chainTaskId, REVEALING);
                actionResponse = taskManagerService.reveal(chainTaskId, extra);
                nextStatus = actionResponse.isSuccess() ? REVEALED : REVEAL_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_UPLOAD:
                updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADING);
                actionResponse = taskManagerService.uploadResult(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? RESULT_UPLOADED : RESULT_UPLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_CONTRIBUTE_AND_FINALIZE:
                updateStatusAndGetNextAction(chainTaskId, CONTRIBUTE_AND_FINALIZE_ONGOING);
                actionResponse = taskManagerService.contributeAndFinalize(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? CONTRIBUTE_AND_FINALIZE_DONE : CONTRIBUTE_AND_FINALIZE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_COMPLETE:
                updateStatusAndGetNextAction(chainTaskId, COMPLETING);
                actionResponse = taskManagerService.complete(chainTaskId);
                subscriptionService.unsubscribeFromTopic(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? COMPLETED : COMPLETE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
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
            case PLEASE_CONTINUE:
            case PLEASE_WAIT:
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

    private boolean storeWorkerpoolAuthAndSmsFromExtraIfPresent(TaskNotificationExtra extra) {
        boolean success = true;
        if (extra != null && extra.getWorkerpoolAuthorization() != null) {
            success = workerpoolAuthorizationService
                    .putWorkerpoolAuthorization(extra.getWorkerpoolAuthorization());
            if (success && extra.getSmsUrl() != null) {
                String chainTaskId = extra.getWorkerpoolAuthorization().getChainTaskId();
                smsService.attachSmsUrlToTask(chainTaskId, extra.getSmsUrl());
            }
        }
        return success;
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

    TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatusUpdate statusUpdate) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]",
                chainTaskId, statusUpdate.getStatus(), statusUpdate.getDetailsWithoutLogs());

        TaskNotificationType next = null;

        // As long as the Core doesn't reply, we try to contact it. It may be rebooting.
        while (next == null && !isFinalDeadlineReached(chainTaskId, Instant.now().toEpochMilli())) {
            // Let's wait for the STOMP session to be ready.
            // Otherwise, an update could be lost.
            try {
                subscriptionService.waitForSessionReady();
            } catch (InterruptedException e) {
                log.warn("Replicate status update has been interrupted" +
                                " [chainTaskId:{}, statusUpdate:{}]",
                        chainTaskId, statusUpdate);
                Thread.currentThread().interrupt();
                return null;
            }
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
                //TODO: Handle case where task exists on-chain but call on Ethereum node failed
                return true;
            }

            finalDeadline = oTask.get().getFinalDeadline();
            finalDeadlineForTask.put(chainTaskId, finalDeadline);
        }

        return now >= finalDeadline;
    }
}
