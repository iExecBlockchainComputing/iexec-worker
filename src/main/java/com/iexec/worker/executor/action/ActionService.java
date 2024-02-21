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
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;

@Service
@AllArgsConstructor
@Slf4j
public class ActionService {
    private SubscriptionService subscriptionService;
    private CustomCoreFeignClient customCoreFeignClient;
    private final Map<String, Long> finalDeadlineForTask = ExpiringMap
            .builder()
            .expiration(1, TimeUnit.HOURS)
            .build();
    private IexecHubService iexecHubService;
    private String workerWalletAddress;
    private ResultService resultService;
    private ComputeManagerService computeManagerService;

    public TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                             ReplicateStatus status) {
        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(status);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    public TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                             ReplicateStatus status,
                                                             ReplicateStatusCause cause) {
        ReplicateStatusUpdate statusUpdate = new ReplicateStatusUpdate(status, cause);
        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    public TaskNotificationType updateStatusAndGetNextAction(String chainTaskId,
                                                             ReplicateStatus status,
                                                             ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .status(status)
                .details(details)
                .build();

        return updateStatusAndGetNextAction(chainTaskId, statusUpdate);
    }

    TaskNotificationType updateStatusAndGetNextAction(String chainTaskId, ReplicateStatusUpdate statusUpdate) {
        log.info("update replicate request [chainTaskId:{}, status:{}, details:{}]",
                chainTaskId, statusUpdate.getStatus(), statusUpdate.getDetailsWithoutLogs());

        TaskNotificationType next = null;

        // As long as the Core doesn't reply, we try to contact it. It may be rebooting.
        while (next == null && !isFinalDeadlineReached(chainTaskId, Instant.now().toEpochMilli())) {
            // Let's wait for the STOMP session to be ready.
            // Otherwise, an update could be lost.
            try {
                subscriptionService.waitForSessionReady();
            } catch (InterruptedException e) {
                log.warn("Replicate status update has been interrupted" +
                                " [chainTaskId:{}, statusUpdate:{}]",
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
     * @param now         Time to check final deadline against.
     * @return {@literal true} if the final deadline is met or the task is unknown on-chain,
     * {@literal false} otherwise.
     */
    boolean isFinalDeadlineReached(String chainTaskId, long now) {
        final Long finalDeadline;

        if (finalDeadlineForTask.containsKey(chainTaskId)) {
            finalDeadline = finalDeadlineForTask.get(chainTaskId);
        } else {
            final Optional<ChainTask> oTask = iexecHubService.getChainTask(chainTaskId);
            if (oTask.isEmpty()) {
                //TODO: Handle case where task exists on-chain but call on Ethereum node failed
                return true;
            }

            finalDeadline = oTask.get().getFinalDeadline();
            finalDeadlineForTask.put(chainTaskId, finalDeadline);
        }

        return now >= finalDeadline;
    }

    ReplicateActionResponse getFailureResponseAndPrintError(ReplicateStatusCause cause, String context, String chainTaskId) {
        logError(cause, context, chainTaskId);
        return ReplicateActionResponse.failure(cause);
    }

    /**
     * This method, which a <String> 'cause' should disappear at some point
     * Each error should have it proper ReplicateStatusCause so the core could
     * keep track of it.
     */
    void logError(String cause, String failureContext,
                  String chainTaskId) {
        log.error("Failed to {} [chainTaskId:'{}', cause:'{}']", failureContext,
                chainTaskId, cause);
    }

    void logError(ReplicateStatusCause cause, String failureContext,
                  String chainTaskId) {
        logError(cause != null ? cause.toString() : "", failureContext,
                chainTaskId);
    }

    ReplicateActionResponse triggerPostComputeHookOnError(String chainTaskId,
                                                          String context,
                                                          TaskDescription taskDescription,
                                                          ReplicateStatus errorStatus,
                                                          ReplicateStatusCause errorCause) {
        // log original error
        logError(errorCause, context, chainTaskId);
        boolean isOk = resultService.writeErrorToIexecOut(chainTaskId, errorStatus, errorCause);
        // try to run post-compute
        if (isOk && computeManagerService.runPostCompute(taskDescription, null).isSuccessful()) {
            //Graceful error, worker will be prompt to contribute
            return ReplicateActionResponse.failure(errorCause);
        }
        //Download failed hard, worker cannot contribute
        logError(POST_COMPUTE_FAILED_UNKNOWN_ISSUE, context, chainTaskId);
        return ReplicateActionResponse.failure(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    boolean hasEnoughGas() {
        if (iexecHubService.hasEnoughGas()) {
            return true;
        }

        String noEnoughGas = String.format("Out of gas! please refill your " +
                        "wallet [walletAddress:%s]",
                workerWalletAddress);
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    boolean isValidChainReceipt(String chainTaskId,
                                ChainReceipt chainReceipt) {
        if (chainReceipt == null) {
            log.warn("The chain receipt is empty, nothing will be sent to the" +
                    " core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (chainReceipt.getBlockNumber() == 0) {
            log.warn("The blockNumber of the receipt is equal to 0, status " +
                    "will not be updated in the core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }
}
