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
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
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
public class ContributeAction extends AbstractAction {
    private static final String CONTRIBUTE = "contribute";

    private ContributionService contributionService;
    private ResultService resultService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_CONTRIBUTE;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, CONTRIBUTING);
        final ReplicateActionResponse actionResponse = contribute(chainTaskId);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? CONTRIBUTED : CONTRIBUTE_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    private ReplicateActionResponse contribute(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            return actionService.getFailureResponseAndPrintError(oErrorStatus.get(),
                    CONTRIBUTE, chainTaskId);
        }

        if (!actionService.hasEnoughGas()) {
            return actionService.getFailureResponseAndPrintError(OUT_OF_GAS,
                    CONTRIBUTE, chainTaskId);
        }

        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        if (computedFile == null) {
            actionService.logError("computed file error", CONTRIBUTE, chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        Contribution contribution = contributionService.getContribution(computedFile);
        if (contribution == null) {
            actionService.logError("get contribution error", CONTRIBUTE, chainTaskId);
            return ReplicateActionResponse.failure(ENCLAVE_SIGNATURE_NOT_FOUND);//TODO update status
        }

        ReplicateActionResponse response = ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID);
        log.debug("contribute [contribution:{}]", contribution);
        Optional<ChainReceipt> oChainReceipt = contributionService.contribute(contribution);

        if (oChainReceipt.isPresent() && actionService.isValidChainReceipt(chainTaskId, oChainReceipt.get())) {
            response = ReplicateActionResponse.success(oChainReceipt.get());
        }
        return response;
    }
}
