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
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.result.ResultService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.RESULT_LINK_MISSING;

@Component
@AllArgsConstructor
@Slf4j
public class UploadAction extends AbstractAction {
    private ResultService resultService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_UPLOAD;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, RESULT_UPLOADING);
        final ReplicateActionResponse actionResponse = uploadResult(chainTaskId);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? RESULT_UPLOADED : RESULT_UPLOAD_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse uploadResult(String chainTaskId) {
        String resultLink = resultService.uploadResultAndGetLink(chainTaskId);
        String context = "upload result";
        if (resultLink.isEmpty()) {
            return actionService.getFailureResponseAndPrintError(RESULT_LINK_MISSING,
                    context, chainTaskId
            );
        }

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String callbackData = computedFile != null ?
                computedFile.getCallbackData() : "";

        log.info("Result uploaded [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        return ReplicateActionResponse.success(resultLink, callbackData);
    }

}
