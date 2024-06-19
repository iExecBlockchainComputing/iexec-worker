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

package com.iexec.worker.chain;


import com.iexec.common.contribution.Contribution;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.worker.config.ConfigServerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.iexec.commons.poco.chain.ChainContributionStatus.CONTRIBUTED;
import static com.iexec.commons.poco.chain.ChainContributionStatus.REVEALED;
import static com.iexec.commons.poco.utils.BytesUtils.bytesToString;
import static com.iexec.commons.poco.utils.BytesUtils.stringToBytes;


@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService implements Purgeable {

    private static final String PENDING_RECEIPT_STATUS = "pending";
    private final SignerService signerService;
    private final ThreadPoolExecutor executor;
    private final Web3jService web3jService;

    @Autowired
    public IexecHubService(SignerService signerService,
                           Web3jService web3jService,
                           ConfigServerConfigurationService configServerConfigurationService) {
        super(signerService.getCredentials(),
                web3jService,
                configServerConfigurationService.getIexecHubContractAddress(),
                1,
                5);
        this.signerService = signerService;
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }

    // region contribute
    IexecHubContract.TaskContributeEventResponse contribute(Contribution contribution) {
        log.info("contribute request [chainTaskId:{}, waitingTxCount:{}]", contribution.getChainTaskId(), getWaitingTransactionCount());
        return sendContributeTransaction(contribution);
    }

    private IexecHubContract.TaskContributeEventResponse sendContributeTransaction(Contribution contribution) {
        String chainTaskId = contribution.getChainTaskId();

        RemoteCall<TransactionReceipt> contributeCall = iexecHubContract.contribute(
                stringToBytes(chainTaskId),
                stringToBytes(contribution.getResultHash()),
                stringToBytes(contribution.getResultSeal()),
                contribution.getEnclaveChallenge(),
                stringToBytes(contribution.getEnclaveSignature()),
                stringToBytes(contribution.getWorkerPoolSignature()));
        log.info("Sent contribute [chainTaskId:{}, contribution:{}]", chainTaskId, contribution);

        TransactionReceipt contributeReceipt = submit(chainTaskId, "contribute", contributeCall);

        List<IexecHubContract.TaskContributeEventResponse> contributeEvents =
                IexecHubContract.getTaskContributeEvents(contributeReceipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId)
                                && Objects.equals(event.worker, signerService.getAddress()))
                        .collect(Collectors.toList());
        log.debug("contributeEvents count {} [chainTaskId: {}]", contributeEvents.size(), chainTaskId);

        if (!contributeEvents.isEmpty()) {
            IexecHubContract.TaskContributeEventResponse contributeEvent = contributeEvents.get(0);
            if (isSuccessTx(chainTaskId, contributeEvent.log, CONTRIBUTED)) {
                log.info("Contributed [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                        chainTaskId, contribution, contributeReceipt.getGasUsed(), contributeEvent.log);
                return contributeEvent;
            }
        }

        log.error("Failed to contribute [chainTaskId:{}]", chainTaskId);
        return null;
    }
    // endregion

    // region reveal
    IexecHubContract.TaskRevealEventResponse reveal(String chainTaskId, String resultDigest) {
        log.info("reveal request [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        return sendRevealTransaction(chainTaskId, resultDigest);
    }

    private IexecHubContract.TaskRevealEventResponse sendRevealTransaction(String chainTaskId, String resultDigest) {
        RemoteCall<TransactionReceipt> revealCall = iexecHubContract.reveal(
                stringToBytes(chainTaskId),
                stringToBytes(resultDigest));
        log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);

        TransactionReceipt revealReceipt = submit(chainTaskId, "reveal", revealCall);

        List<IexecHubContract.TaskRevealEventResponse> revealEvents =
                IexecHubContract.getTaskRevealEvents(revealReceipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId)
                                && Objects.equals(event.worker, signerService.getAddress()))
                        .collect(Collectors.toList());
        log.debug("revealEvents count {} [chainTaskId:{}]", revealEvents.size(), chainTaskId);

        if (!revealEvents.isEmpty()) {
            IexecHubContract.TaskRevealEventResponse revealEvent = revealEvents.get(0);
            if (isSuccessTx(chainTaskId, revealEvent.log, REVEALED)) {
                log.info("Revealed [chainTaskId:{}, resultDigest:{}, gasUsed:{}, log:{}]",
                        chainTaskId, resultDigest, revealReceipt.getGasUsed(), revealEvent.log);
                return revealEvent;
            }
        }

        log.error("Failed to reveal [chainTaskId:{}]", chainTaskId);
        return null;
    }
    // endregion reveal

    // region contributeAndFinalize
    public Optional<ChainReceipt> contributeAndFinalize(Contribution contribution, String resultLink, String callbackData) {
        log.info("contributeAndFinalize request [chainTaskId:{}, waitingTxCount:{}]",
                contribution.getChainTaskId(), getWaitingTransactionCount());
        IexecHubContract.TaskFinalizeEventResponse finalizeEvent = sendContributeAndFinalizeTransaction(contribution, resultLink, callbackData);
        return Optional.ofNullable(finalizeEvent)
                .map(event -> ChainUtils.buildChainReceipt(event.log, contribution.getChainTaskId(), getLatestBlockNumber()));
    }

    private IexecHubContract.TaskFinalizeEventResponse sendContributeAndFinalizeTransaction(Contribution contribution, String resultLink, String callbackData) {
        String chainTaskId = contribution.getChainTaskId();

        RemoteCall<TransactionReceipt> contributeAndFinalizeCall = iexecHubContract.contributeAndFinalize(
                stringToBytes(chainTaskId),
                stringToBytes(contribution.getResultDigest()),
                StringUtils.isNotEmpty(resultLink) ? resultLink.getBytes(StandardCharsets.UTF_8) : new byte[0],
                StringUtils.isNotEmpty(callbackData) ? stringToBytes(callbackData) : new byte[0],
                contribution.getEnclaveChallenge(),
                stringToBytes(contribution.getEnclaveSignature()),
                stringToBytes(contribution.getWorkerPoolSignature()));
        log.info("Sent contributeAndFinalize [chainTaskId:{}, contribution:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, contribution, resultLink, callbackData);

        TransactionReceipt receipt = submit(chainTaskId, "contributeAndFinalize", contributeAndFinalizeCall);

        List<IexecHubContract.TaskFinalizeEventResponse> finalizeEvents =
                IexecHubContract.getTaskFinalizeEvents(receipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId))
                        .collect(Collectors.toList());
        log.debug("finalizeEvents count {} [chainTaskId:{}]", finalizeEvents.size(), chainTaskId);

        if (!finalizeEvents.isEmpty()) {
            IexecHubContract.TaskFinalizeEventResponse finalizeEvent = finalizeEvents.get(0);
            if (isSuccessTx(chainTaskId, finalizeEvent.log, REVEALED)) {
                log.info("contributeAndFinalize done [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                        chainTaskId, contribution, receipt.getGasUsed(), finalizeEvent.log);
                return finalizeEvent;
            }
        }

        log.error("contributeAndFinalize failed [chainTaskId:{}]", chainTaskId);
        return null;
    }
    // endregion

    // region isSuccessTx
    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - 1 - executor.getCompletedTaskCount();
    }

    boolean isSuccessTx(String chainTaskId, Log eventLog, ChainContributionStatus pretendedStatus) {
        if (eventLog == null) {
            return false;
        }

        log.info("event log type {}", eventLog.getType());
        if (PENDING_RECEIPT_STATUS.equals(eventLog.getType())) {
            return isStatusValidOnChainAfterPendingReceipt(chainTaskId, pretendedStatus);
        }

        return true;
    }

    private boolean isContributionStatusValidOnChain(String chainTaskId, ChainContributionStatus chainContributionStatus) {
        Optional<ChainContribution> chainContribution = getChainContribution(chainTaskId);
        return chainContribution.isPresent() && chainContribution.get().getStatus() == chainContributionStatus;
    }

    private boolean isStatusValidOnChainAfterPendingReceipt(String chainTaskId, ChainContributionStatus onchainStatus) {
        long maxWaitingTime = 10 * web3jService.getBlockTime().toMillis();
        log.info("Waiting for on-chain status after pending receipt " +
                        "[chainTaskId:{}, status:{}, maxWaitingTime:{}]",
                chainTaskId, onchainStatus, maxWaitingTime);

        final long startTime = System.currentTimeMillis();
        long duration = 0;
        while (duration < maxWaitingTime) {
            try {
                if (isContributionStatusValidOnChain(chainTaskId, onchainStatus)) {
                    return true;
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("Error in checking the latest block number", e);
                Thread.currentThread().interrupt();
            }
            duration = System.currentTimeMillis() - startTime;
        }

        log.error("Timeout reached after waiting for on-chain status " +
                        "[chainTaskId:{}, maxWaitingTime:{}]",
                chainTaskId, maxWaitingTime);
        return false;
    }
    // endregion

    Optional<ChainContribution> getChainContribution(String chainTaskId) {
        return getChainContribution(chainTaskId, signerService.getAddress());
    }

    Optional<ChainAccount> getChainAccount() {
        return getChainAccount(signerService.getAddress());
    }

    public boolean hasEnoughGas() {
        return web3jService.hasEnoughGas(signerService.getAddress());
    }

    public long getLatestBlockNumber() {
        return web3jService.getLatestBlockNumber();
    }

    boolean isChainTaskActive(String chainTaskId) {
        Optional<ChainTask> chainTask = getChainTask(chainTaskId);
        return chainTask.filter(task -> task.getStatus() == ChainTaskStatus.ACTIVE).isPresent();
    }

    boolean isChainTaskRevealing(String chainTaskId) {
        Optional<ChainTask> chainTask = getChainTask(chainTaskId);
        return chainTask.filter(task -> task.getStatus() == ChainTaskStatus.REVEALING).isPresent();
    }

    @Override
    public boolean purgeTask(String chainTaskId) {
        return super.purgeTask(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        super.purgeAllTasksData();
    }

    TransactionReceipt submit(String chainTaskId, String transactionType, RemoteCall<TransactionReceipt> remoteCall) {
        try {
            final RemoteCallTask remoteCallSend = new RemoteCallTask(chainTaskId, transactionType, remoteCall);
            return submit(remoteCallSend);
        } catch (ExecutionException e) {
            log.error("{} asynchronous execution did not complete", transactionType, e);
        } catch (InterruptedException e) {
            log.error("{} thread has been interrupted", transactionType, e);
            Thread.currentThread().interrupt();
        }
        // return non-null receipt with empty logs on failure
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of());
        return receipt;
    }

    TransactionReceipt submit(RemoteCallTask remoteCallTask) throws ExecutionException, InterruptedException {
        Future<TransactionReceipt> future = executor.submit(remoteCallTask);
        return future.get();
    }

    /**
     * Sends a transaction to the blockchain and returns its receipt.
     */
    static class RemoteCallTask implements Callable<TransactionReceipt> {
        private final String chainTaskId;
        private final String transactionType;
        private final RemoteCall<TransactionReceipt> remoteCall;

        public RemoteCallTask(String chainTaskId, String transactionType, RemoteCall<TransactionReceipt> remoteCall) {
            this.chainTaskId = chainTaskId;
            this.transactionType = transactionType;
            this.remoteCall = remoteCall;
        }

        @Override
        public TransactionReceipt call() throws Exception {
            TransactionReceipt receipt = remoteCall.send();
            log.debug("{} transaction hash {} at block {} [chainTaskId:{}]",
                    transactionType, receipt.getTransactionHash(), receipt.getBlockNumber(), chainTaskId);
            log.info("{} receipt [chainTaskId:{}]", transactionType, chainTaskId);
            return receipt;
        }
    }
}
