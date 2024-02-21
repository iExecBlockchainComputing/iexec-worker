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

import com.iexec.common.contribution.Contribution;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.result.ResultService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.*;

@Component
@AllArgsConstructor
@Slf4j
public class ContributeAndFinalizeAction extends AbstractAction {
    private static final String CONTRIBUTE_AND_FINALIZE = "contributeAndFinalize";
    private ContributionService contributionService;
    private ResultService resultService;
    private IexecHubService iexecHubService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_CONTRIBUTE_AND_FINALIZE;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, CONTRIBUTE_AND_FINALIZE_ONGOING);
        final ReplicateActionResponse actionResponse = contributeAndFinalize(chainTaskId);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? CONTRIBUTE_AND_FINALIZE_DONE : CONTRIBUTE_AND_FINALIZE_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse contributeAndFinalize(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(),
                    CONTRIBUTE_AND_FINALIZE, chainTaskId);
        }

        if (!actionService.hasEnoughGas()) {
            return actionService.getFailureResponseAndPrintError(OUT_OF_GAS,
                    CONTRIBUTE_AND_FINALIZE, chainTaskId);
        }

        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        if (computedFile == null) {
            actionService.logError("computed file error", CONTRIBUTE_AND_FINALIZE, chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        Contribution contribution = contributionService.getContribution(computedFile);
        if (contribution == null) {
            actionService.logError("get contribution error", CONTRIBUTE_AND_FINALIZE, chainTaskId);
            return ReplicateActionResponse.failure(ENCLAVE_SIGNATURE_NOT_FOUND);//TODO update status
        }

        ReplicateActionResponse response = ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID);
        oErrorStatus = contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(),
                    CONTRIBUTE_AND_FINALIZE, chainTaskId);
        }

        String callbackData = computedFile.getCallbackData();
        String resultLink = resultService.uploadResultAndGetLink(chainTaskId);
        log.debug("contributeAndFinalize [contribution:{}, resultLink:{}, callbackData:{}]",
                contribution, resultLink, callbackData);
        Optional<ChainReceipt> oChainReceipt = iexecHubService.contributeAndFinalize(contribution, resultLink, callbackData);

        if (oChainReceipt.isPresent() && actionService.isValidChainReceipt(chainTaskId, oChainReceipt.get())) {
            final ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                    .resultLink(resultLink)
                    .chainCallbackData(callbackData)
                    .chainReceipt(oChainReceipt.get())
                    .build();
            response = ReplicateActionResponse.builder()
                    .isSuccess(true)
                    .details(details)
                    .build();
        }
        return response;
    }
}
