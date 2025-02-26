/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.notification.TaskAbortCause;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.iexec.common.replicate.ReplicateStatus.*;

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

    public TaskNotificationService(final TaskManagerService taskManagerService,
                                   final CustomCoreFeignClient customCoreFeignClient,
                                   final ApplicationEventPublisher applicationEventPublisher,
                                   final SubscriptionService subscriptionService,
                                   final WorkerpoolAuthorizationService workerpoolAuthorizationService,
                                   final IexecHubService iexecHubService,
                                   final SmsService smsService) {
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
    public void onTaskNotification(final TaskNotification notification) {
        final String chainTaskId = notification.getChainTaskId();
        final TaskNotificationType action = notification.getTaskNotificationType();
        final ReplicateActionResponse actionResponse;
        final ReplicateStatus nextStatus;
        TaskNotificationType nextAction = null;
        log.debug("Received TaskNotification [chainTaskId:{}, action:{}]", chainTaskId, action);

        if (action == null) {
            log.error("No action to do [chainTaskId:{}]", chainTaskId);
            return;
        }

        final TaskNotificationExtra extra = notification.getTaskNotificationExtra();

        if (!storeWorkerpoolAuthAndSmsFromExtraIfPresent(extra)) {
            log.error("Should storeWorkerpoolAuthorizationFromExtraIfPresent [chainTaskId:{}]", chainTaskId);
            return;
        }
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        switch (action) {
            case PLEASE_START:
                updateStatus(chainTaskId, STARTING);
                actionResponse = taskManagerService.start(taskDescription);
                nextStatus = actionResponse.isSuccess() ? STARTED : START_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_DOWNLOAD_APP:
                updateStatus(chainTaskId, APP_DOWNLOADING);
                actionResponse = taskManagerService.downloadApp(taskDescription);
                nextStatus = actionResponse.isSuccess() ? APP_DOWNLOADED : APP_DOWNLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_DOWNLOAD_DATA:
                updateStatus(chainTaskId, DATA_DOWNLOADING);
                actionResponse = taskManagerService.downloadData(taskDescription);
                nextStatus = actionResponse.isSuccess() ? DATA_DOWNLOADED : DATA_DOWNLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_COMPUTE:
                updateStatus(chainTaskId, COMPUTING);
                actionResponse = taskManagerService.compute(taskDescription);
                if (actionResponse.getDetails() != null) {
                    actionResponse.getDetails().tailLogs();
                }
                nextStatus = actionResponse.isSuccess() ? COMPUTED : COMPUTE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_CONTRIBUTE:
                updateStatus(chainTaskId, CONTRIBUTING);
                actionResponse = taskManagerService.contribute(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? CONTRIBUTED : CONTRIBUTE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_REVEAL:
                updateStatus(chainTaskId, REVEALING);
                actionResponse = taskManagerService.reveal(chainTaskId, extra);
                nextStatus = actionResponse.isSuccess() ? REVEALED : REVEAL_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_UPLOAD:
                updateStatus(chainTaskId, RESULT_UPLOADING);
                actionResponse = taskManagerService.uploadResult(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? RESULT_UPLOADED : RESULT_UPLOAD_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_CONTRIBUTE_AND_FINALIZE:
                updateStatus(chainTaskId, CONTRIBUTE_AND_FINALIZE_ONGOING);
                actionResponse = taskManagerService.contributeAndFinalize(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? CONTRIBUTE_AND_FINALIZE_DONE : CONTRIBUTE_AND_FINALIZE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_COMPLETE:
                updateStatus(chainTaskId, COMPLETING);
                actionResponse = taskManagerService.complete(chainTaskId);
                nextStatus = actionResponse.isSuccess() ? COMPLETED : COMPLETE_FAILED;
                nextAction = updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
                break;
            case PLEASE_ABORT:
                if (!taskManagerService.abort(chainTaskId)) {
                    log.error("Failed to abort task [chainTaskId:{}]", chainTaskId);
                    return;
                }
                final TaskAbortCause taskAbortCause = notification.getTaskAbortCause();
                final ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(
                        ABORTED, taskAbortCause.toReplicateStatusCause());
                updateStatusAndGetNextAction(chainTaskId, statusUpdate);
                break;
            case PLEASE_CONTINUE,
                 PLEASE_WAIT:
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

    private boolean storeWorkerpoolAuthAndSmsFromExtraIfPresent(final TaskNotificationExtra extra) {
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

    private void updateStatus(final String chainTaskId,
                              final ReplicateStatus status) {
        final ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(status);
        updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    private TaskNotificationType updateStatusAndGetNextAction(final String chainTaskId,
                                                              final ReplicateStatus status,
                                                              final ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .status(status)
                .details(details)
                .build();

        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    TaskNotificationType updateStatusAndGetNextAction(final String chainTaskId,
                                                      final ReplicateStatusUpdate statusUpdate) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]",
                chainTaskId, statusUpdate.getStatus(), statusUpdate.getDetailsWithoutLogs());

        TaskNotificationType next = null;

        // As long as the Core doesn't reply, we try to contact it. It may be rebooting.
        while (next == null && !isFinalDeadlineReached(chainTaskId)) {
            // Let's wait for the STOMP session to be ready.
            // Otherwise, an update could be lost.
            try {
                subscriptionService.waitForSessionReady();
            } catch (InterruptedException e) {
                log.warn("Replicate status update has been interrupted [chainTaskId:{}, statusUpdate:{}]",
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
     * @return {@literal true} if the final deadline is met or the task is unknown on-chain, {@literal false} otherwise.
     */
    boolean isFinalDeadlineReached(final String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        return taskDescription != null && taskDescription.getFinalDeadline() < Instant.now().toEpochMilli();
    }
}
