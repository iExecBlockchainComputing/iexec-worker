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
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationExtra;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.RevealService;
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
public class RevealAction extends AbstractAction {
    private ResultService resultService;
    private RevealService revealService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_REVEAL;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, REVEALING);
        final ReplicateActionResponse actionResponse = reveal(chainTaskId, notification.getTaskNotificationExtra());
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? REVEALED : REVEAL_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse reveal(String chainTaskId,
                                   TaskNotificationExtra extra) {
        String context = "reveal";
        if (extra == null || extra.getBlockNumber() == 0) {
            return actionService.getFailureResponseAndPrintError(CONSENSUS_BLOCK_MISSING,
                    context, chainTaskId);
        }
        long consensusBlock = extra.getBlockNumber();

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String resultDigest = computedFile != null ?
                computedFile.getResultDigest() : "";

        if (resultDigest.isEmpty()) {
            actionService.logError("get result digest error", context, chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        if (!revealService.isConsensusBlockReached(chainTaskId,
                consensusBlock)) {
            return actionService.getFailureResponseAndPrintError(BLOCK_NOT_REACHED,
                    context, chainTaskId
            );
        }

        if (!revealService.repeatCanReveal(chainTaskId,
                resultDigest)) {
            return actionService.getFailureResponseAndPrintError(CANNOT_REVEAL,
                    context, chainTaskId);
        }

        if (!actionService.hasEnoughGas()) {
            actionService.logError(OUT_OF_GAS, context, chainTaskId);
            // Don't we prefer an OUT_OF_GAS?
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt =
                revealService.reveal(chainTaskId, resultDigest);
        if (oChainReceipt.isEmpty() ||
                !actionService.isValidChainReceipt(chainTaskId, oChainReceipt.get())) {
            return actionService.getFailureResponseAndPrintError(CHAIN_RECEIPT_NOT_VALID,
                    context, chainTaskId
            );
        }

        return ReplicateActionResponse.success(oChainReceipt.get());
    }
}
