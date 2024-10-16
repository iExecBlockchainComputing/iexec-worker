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
import com.iexec.commons.poco.encoding.PoCoDataEncoder;
import com.iexec.worker.config.ConfigServerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private final String hubContractAddress;
    private final Duration blockTime;

    @Autowired
    public IexecHubService(SignerService signerService,
                           Web3jService web3jService,
                           ConfigServerConfigurationService configServerConfigurationService) {
        super(signerService.getCredentials(),
                web3jService,
                configServerConfigurationService.getIexecHubContractAddress(),
                1,
                5);
        this.hubContractAddress = configServerConfigurationService.getIexecHubContractAddress();
        this.blockTime = configServerConfigurationService.getBlockTime();
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
        final String chainTaskId = contribution.getChainTaskId();
        final String txData = PoCoDataEncoder.encodeContribute(
                chainTaskId,
                contribution.getResultHash(),
                contribution.getResultSeal(),
                contribution.getEnclaveChallenge(),
                contribution.getEnclaveSignature(),
                contribution.getWorkerPoolSignature()
        );
        log.info("Sent contribute [chainTaskId:{}, contribution:{}]", chainTaskId, contribution);

        final TransactionReceipt contributeReceipt = submit("contribute", txData);

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
        final String txData = PoCoDataEncoder.encodeReveal(
                chainTaskId,
                resultDigest
        );
        log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);

        final TransactionReceipt revealReceipt = submit("reveal", txData);

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
        final String chainTaskId = contribution.getChainTaskId();
        final String txData = PoCoDataEncoder.encodeContributeAndFinalize(
                chainTaskId,
                contribution.getResultDigest(),
                StringUtils.isNotEmpty(resultLink) ? resultLink.getBytes(StandardCharsets.UTF_8) : new byte[0],
                StringUtils.isNotEmpty(callbackData) ? stringToBytes(callbackData) : new byte[0],
                contribution.getEnclaveChallenge(),
                contribution.getEnclaveSignature(),
                contribution.getWorkerPoolSignature()
        );
        log.info("Sent contributeAndFinalize [chainTaskId:{}, contribution:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, contribution, resultLink, callbackData);

        final TransactionReceipt receipt = submit("contributeAndFinalize", txData);

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

    synchronized TransactionReceipt submit(String txType, String txData) {
        try {
            final BigInteger nonce = signerService.getNonce();
            final String txHash = signerService.signAndSendTransaction(
                    nonce, web3jService.getUserGasPrice(), hubContractAddress, txData);
            return waitTxMined(txHash);
        } catch (IOException e) {
            log.error("{} asynchronous execution did not complete", txType, e);
        } catch (InterruptedException e) {
            log.error("{} thread has been interrupted", txType, e);
            Thread.currentThread().interrupt();
        }
        // return non-null receipt with empty logs on failure
        final TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of());
        return receipt;
    }

    TransactionReceipt waitTxMined(String txHash) throws InterruptedException {
        int attempt = 0;
        while (web3jService.getTransactionReceipt(txHash) == null && attempt < 3 * blockTime.toSeconds()) {
            TimeUnit.SECONDS.sleep(1L);
            attempt++;
        }
        return web3jService.getTransactionReceipt(txHash);
    }
}
