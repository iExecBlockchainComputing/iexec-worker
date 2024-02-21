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

package com.iexec.worker.executor;

import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationExtra;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.executor.action.AbstractAction;
import com.iexec.worker.executor.action.ActionService;
import com.iexec.worker.sms.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.ABORTED;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_DESCRIPTION_NOT_FOUND;
import static java.util.function.Predicate.not;


@Slf4j
@Service
public class TaskNotificationService {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkerpoolAuthorizationService workerpoolAuthorizationService;
    private final IexecHubService iexecHubService;
    private final SmsService smsService;
    private final ActionService actionService;
    private final AbortionService abortionService;

    private final Map<TaskNotificationType, AbstractAction> actionForNotification;

    public TaskNotificationService(
            ApplicationEventPublisher applicationEventPublisher,
            WorkerpoolAuthorizationService workerpoolAuthorizationService,
            IexecHubService iexecHubService,
            SmsService smsService,
            ActionService actionService,
            AbortionService abortionService,
            List<AbstractAction> actions) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.workerpoolAuthorizationService = workerpoolAuthorizationService;
        this.iexecHubService = iexecHubService;
        this.smsService = smsService;
        this.actionService = actionService;
        this.abortionService = abortionService;

        this.actionForNotification = actions.stream()
                .collect(
                        Collectors.toMap(
                                AbstractAction::getAction,
                                Function.identity()
                        ));
    }

    @PostConstruct
    void checkAllActionsAreMapped() {
        final List<TaskNotificationType> unmappedActions = Arrays.stream(TaskNotificationType.values())
                .filter(not(actionForNotification::containsKey))
                .collect(Collectors.toList());

        if (!unmappedActions.isEmpty()) {
            log.error("Some notifications can't be handled as they are not mapped to any action [unmappedActions:{}]",
                    unmappedActions);
            throw new IllegalArgumentException("Some notifications can't be handled as they are not mapped to any action");
        }
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
            abortionService.abort(chainTaskId);
            actionService.updateStatusAndGetNextAction(chainTaskId, ABORTED, TASK_DESCRIPTION_NOT_FOUND);
            return;
        }

        final AbstractAction function = actionForNotification.get(action);
        final TaskNotificationType nextAction = function.executeAction(taskDescription, notification);
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
}
