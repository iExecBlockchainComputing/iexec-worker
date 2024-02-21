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
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.utils.WorkflowException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static java.util.Objects.requireNonNull;

@Component
@AllArgsConstructor
@Slf4j
public class DownloadDataAction extends AbstractAction {
    private ContributionService contributionService;
    private DataService dataService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_DOWNLOAD_DATA;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, DATA_DOWNLOADING);
        final ReplicateActionResponse actionResponse = downloadData(taskDescription);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? DATA_DOWNLOADED : DATA_DOWNLOAD_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse downloadData(TaskDescription taskDescription) {
        requireNonNull(taskDescription, "task description must not be null");
        String chainTaskId = taskDescription.getChainTaskId();
        Optional<ReplicateStatusCause> errorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "download data";
        if (errorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(errorStatus.get(),
                    context, chainTaskId);
        }
        // Return early if TEE task
        if (taskDescription.isTeeTask()) {
            log.info("Dataset and input files will be downloaded by the pre-compute enclave [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.success();
        }
        try {
            // download dataset for standard task
            if (!taskDescription.containsDataset()) {
                log.info("No dataset for this task [chainTaskId:{}]", chainTaskId);
            } else {
                String datasetUri = taskDescription.getDatasetUri();
                log.info("Downloading dataset [chainTaskId:{}, uri:{}, name:{}]",
                        chainTaskId, datasetUri, taskDescription.getDatasetName());
                dataService.downloadStandardDataset(taskDescription);
            }
            // download input files for standard task
            if (!taskDescription.containsInputFiles()) {
                log.info("No input files for this task [chainTaskId:{}]", chainTaskId);
            } else {
                log.info("Downloading input files [chainTaskId:{}]", chainTaskId);
                dataService.downloadStandardInputFiles(chainTaskId, taskDescription.getInputFiles());
            }
        } catch (WorkflowException e) {
            return actionService.triggerPostComputeHookOnError(chainTaskId, context, taskDescription,
                    DATA_DOWNLOAD_FAILED, e.getReplicateStatusCause());
        }
        return ReplicateActionResponse.success();
    }
}
