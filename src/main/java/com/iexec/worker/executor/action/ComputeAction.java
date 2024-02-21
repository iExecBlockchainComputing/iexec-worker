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

import com.iexec.common.replicate.*;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.APP_NOT_FOUND_LOCALLY;
import static com.iexec.common.replicate.ReplicateStatusCause.TEE_PREPARATION_FAILED;

@Component
@AllArgsConstructor
@Slf4j
public class ComputeAction extends AbstractAction {
    private ContributionService contributionService;
    private ComputeManagerService computeManagerService;
    private TeeServicesManager teeServicesManager;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_COMPLETE;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, COMPUTING);
        final ReplicateActionResponse actionResponse = compute(taskDescription);
        if (actionResponse.getDetails() != null) {
            actionResponse.getDetails().tailLogs();
        }
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? COMPUTED : COMPUTE_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse compute(TaskDescription taskDescription) {
        final String chainTaskId = taskDescription.getChainTaskId();
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "compute";
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(), context, chainTaskId);
        }

        if (!computeManagerService.isAppDownloaded(taskDescription.getAppUri())) {
            return actionService.getFailureResponseAndPrintError(APP_NOT_FOUND_LOCALLY,
                    context, chainTaskId);
        }

        if (taskDescription.isTeeTask()) {
            TeeService teeService = teeServicesManager.getTeeService(taskDescription.getTeeFramework());
            if (!teeService.prepareTeeForTask(chainTaskId)) {
                return actionService.getFailureResponseAndPrintError(TEE_PREPARATION_FAILED,
                        context, chainTaskId);
            }
        }

        WorkerpoolAuthorization workerpoolAuthorization =
                contributionService.getWorkerpoolAuthorization(chainTaskId);

        PreComputeResponse preResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        if (!preResponse.isSuccessful()) {
            final ReplicateActionResponse failureResponseAndPrintError;
            failureResponseAndPrintError = actionService.getFailureResponseAndPrintError(
                    preResponse.getExitCause(),
                    context,
                    chainTaskId
            );
            return failureResponseAndPrintError;
        }

        AppComputeResponse appResponse =
                computeManagerService.runCompute(taskDescription,
                        preResponse.getSecureSession());
        if (!appResponse.isSuccessful()) {
            final ReplicateStatusCause cause = appResponse.getExitCause();
            actionService.logError(cause, context, chainTaskId);
            return ReplicateActionResponse.failureWithDetails(
                    ReplicateStatusDetails.builder()
                            .cause(cause)
                            .exitCode(appResponse.getExitCode())
                            .computeLogs(
                                    ComputeLogs.builder()
                                            .stdout(appResponse.getStdout())
                                            .stderr(appResponse.getStderr())
                                            .build()
                            )
                            .build());
        }

        PostComputeResponse postResponse =
                computeManagerService.runPostCompute(taskDescription,
                        preResponse.getSecureSession());
        if (!postResponse.isSuccessful()) {
            ReplicateStatusCause cause = postResponse.getExitCause();
            actionService.logError(cause, context, chainTaskId);
            return ReplicateActionResponse.failureWithStdout(cause,
                    postResponse.getStdout());
        }
        return ReplicateActionResponse.successWithLogs(
                ComputeLogs.builder()
                        .stdout(appResponse.getStdout())
                        .stderr(appResponse.getStderr())
                        .build()
        );
    }

}
