/*
 * Copyright 2024-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.executor.action;

import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_DESCRIPTION_INVALID;

@Component
@AllArgsConstructor
@Slf4j
public class StartAction extends AbstractAction {
    private ContributionService contributionService;
    private TeeServicesManager teeServicesManager;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_START;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();
        actionService.updateStatusAndGetNextAction(chainTaskId, STARTING);
        final ReplicateActionResponse actionResponse = start(taskDescription);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? STARTED : START_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse start(TaskDescription taskDescription) {
        final String chainTaskId = taskDescription.getChainTaskId();
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "start";
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        // result encryption is not supported for standard tasks
        if (!taskDescription.isTeeTask() && taskDescription.isResultEncryption()) {
            return actionService.getFailureResponseAndPrintError(TASK_DESCRIPTION_INVALID,
                    context, chainTaskId);
        }

        if (taskDescription.isTeeTask()) {
            // If any TEE prerequisite is not met,
            // then we won't be able to run the task.
            // So it should be aborted right now.
            final TeeService teeService = teeServicesManager.getTeeService(taskDescription.getTeeFramework());
            final Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(chainTaskId);
            if (teePrerequisitesIssue.isPresent()) {
                log.error("TEE prerequisites are not met [chainTaskId: {}, issue: {}]", chainTaskId, teePrerequisitesIssue.get());
                return actionService.getFailureResponseAndPrintError(teePrerequisitesIssue.get(), context, chainTaskId);
            }
        }
        return ReplicateActionResponse.success();
    }
}
