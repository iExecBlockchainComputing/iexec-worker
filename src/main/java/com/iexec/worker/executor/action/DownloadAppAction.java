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
import com.iexec.worker.compute.ComputeManagerService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.APP_IMAGE_DOWNLOAD_FAILED;

@Component
@AllArgsConstructor
@Slf4j
public class DownloadAppAction extends AbstractAction {

    private ContributionService contributionService;
    private ComputeManagerService computeManagerService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_DOWNLOAD_APP;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, APP_DOWNLOADING);
        final ReplicateActionResponse actionResponse = downloadApp(taskDescription);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? APP_DOWNLOADED : APP_DOWNLOAD_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse downloadApp(TaskDescription taskDescription) {
        final String chainTaskId = taskDescription.getChainTaskId();
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "download app";
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        if (computeManagerService.downloadApp(taskDescription)) {
            return ReplicateActionResponse.success();
        }
        return actionService.triggerPostComputeHookOnError(chainTaskId, context, taskDescription,
                APP_DOWNLOAD_FAILED, APP_IMAGE_DOWNLOAD_FAILED);
    }
}
