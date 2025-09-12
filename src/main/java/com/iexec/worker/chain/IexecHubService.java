/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.encoding.LogTopic;
import com.iexec.commons.poco.encoding.PoCoDataEncoder;
import com.iexec.worker.config.ConfigServerConfigurationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.commons.poco.chain.ChainContributionStatus.CONTRIBUTED;
import static com.iexec.commons.poco.chain.ChainContributionStatus.REVEALED;
import static com.iexec.commons.poco.utils.BytesUtils.stringToBytes;

@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService implements Purgeable {

    private static final String PENDING_RECEIPT_STATUS = "pending";
    private final SignerService signerService;
    private final ThreadPoolExecutor executor;
    private final Web3jService web3jService;
    private final String hubContractAddress;
    private final Counter failureCounter = Metrics.counter("iexec.poco.transaction", "status", "failure");
    private final Counter successCounter = Metrics.counter("iexec.poco.transaction", "status", "success");

    @Autowired
    public IexecHubService(final SignerService signerService,
                           final Web3jService web3jService,
                           final ConfigServerConfigurationService configServerConfigurationService) {
        super(signerService.getCredentials(),
                web3jService,
                configServerConfigurationService.getIexecHubContractAddress(),
                1,
                5);
        this.hubContractAddress = configServerConfigurationService.getIexecHubContractAddress();
        this.signerService = signerService;
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }

    // region contribute
    Log contribute(final Contribution contribution) {
        log.info("contribute request [chainTaskId:{}, waitingTxCount:{}]", contribution.chainTaskId(), getWaitingTransactionCount());

        final String chainTaskId = contribution.chainTaskId();
        final String txData = PoCoDataEncoder.encodeContribute(
                chainTaskId,
                contribution.resultHash(),
                contribution.resultSeal(),
                contribution.enclaveChallenge(),
                contribution.enclaveSignature(),
                contribution.workerPoolSignature()
        );
        log.info("Sent contribute [chainTaskId:{}, contribution:{}]", chainTaskId, contribution);

        final TransactionReceipt receipt = submit("contribute", txData);
        log.debug("receipt {}", receipt);

        final List<Log> contributeEvents = receipt.getLogs().stream()
                .filter(log -> log.getTopics().get(0).equals(LogTopic.TASK_CONTRIBUTE_EVENT)
                        && log.getTopics().get(1).equals(chainTaskId))
                .toList();
        log.debug("contributeEvents count {} [chainTaskId: {}]", contributeEvents.size(), chainTaskId);

        if (!contributeEvents.isEmpty()) {
            final Log contributeEvent = contributeEvents.get(0);
            if (isSuccessTx(chainTaskId, contributeEvent, CONTRIBUTED)) {
                log.info("contribute done [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                        chainTaskId, contribution, receipt.getGasUsed(), contributeEvent);
                return contributeEvent;
            }
        }

        log.error("Failed to contribute [chainTaskId:{}]", chainTaskId);
        return null;
    }
    // endregion

    // region reveal
    Log reveal(final String chainTaskId, final String resultDigest) {
        log.info("reveal request [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());

        final String txData = PoCoDataEncoder.encodeReveal(
                chainTaskId,
                resultDigest
        );
        log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);

        final TransactionReceipt receipt = submit("reveal", txData);

        final List<Log> revealEvents = receipt.getLogs().stream()
                .filter(log -> log.getTopics().get(0).equals(LogTopic.TASK_REVEAL_EVENT)
                        && log.getTopics().get(1).equals(chainTaskId))
                .toList();
        log.debug("revealEvents count {} [chainTaskId:{}]", revealEvents.size(), chainTaskId);

        if (!revealEvents.isEmpty()) {
            final Log revealEvent = revealEvents.get(0);
            if (isSuccessTx(chainTaskId, revealEvent, REVEALED)) {
                log.info("reveal done [chainTaskId:{}, resultDigest:{}, gasUsed:{}, log:{}]",
                        chainTaskId, resultDigest, receipt.getGasUsed(), revealEvent);
                return revealEvent;
            }
        }

        log.error("Failed to reveal [chainTaskId:{}]", chainTaskId);
        return null;
    }
    // endregion reveal

    // region contributeAndFinalize
    public Optional<ChainReceipt> contributeAndFinalize(final Contribution contribution, final String resultLink,
                                                        final String callbackData) {
        log.info("contributeAndFinalize request [chainTaskId:{}, waitingTxCount:{}]",
                contribution.chainTaskId(), getWaitingTransactionCount());

        final String chainTaskId = contribution.chainTaskId();
        final String txData = PoCoDataEncoder.encodeContributeAndFinalize(
                chainTaskId,
                contribution.resultDigest(),
                StringUtils.isNotEmpty(resultLink) ? resultLink.getBytes(StandardCharsets.UTF_8) : new byte[0],
                StringUtils.isNotEmpty(callbackData) ? stringToBytes(callbackData) : new byte[0],
                contribution.enclaveChallenge(),
                contribution.enclaveSignature(),
                contribution.workerPoolSignature()
        );
        log.info("Sent contributeAndFinalize [chainTaskId:{}, contribution:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, contribution, resultLink, callbackData);

        final TransactionReceipt receipt = submit("contributeAndFinalize", txData);

        final List<Log> finalizeEvents = receipt.getLogs().stream()
                .filter(log -> log.getTopics().get(0).equals(LogTopic.TASK_FINALIZE_EVENT)
                        && log.getTopics().get(1).equals(chainTaskId))
                .toList();
        log.debug("finalizeEvents count {} [chainTaskId:{}]", finalizeEvents.size(), chainTaskId);

        if (!finalizeEvents.isEmpty()) {
            final Log finalizeEvent = finalizeEvents.get(0);
            if (isSuccessTx(chainTaskId, finalizeEvent, REVEALED)) {
                log.info("contributeAndFinalize done [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                        chainTaskId, contribution, receipt.getGasUsed(), finalizeEvent);
                return Optional.of(
                        ChainUtils.buildChainReceipt(finalizeEvent, contribution.chainTaskId(), getLatestBlockNumber()));
            }
        }

        log.error("contributeAndFinalize failed [chainTaskId:{}]", chainTaskId);
        return Optional.empty();
    }
    // endregion

    // region isSuccessTx
    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - 1 - executor.getCompletedTaskCount();
    }

    boolean isSuccessTx(final String chainTaskId, final Log eventLog, final ChainContributionStatus pretendedStatus) {
        if (eventLog == null) {
            return false;
        }

        log.info("event log type {}", eventLog.getType());
        if (PENDING_RECEIPT_STATUS.equals(eventLog.getType())) {
            return isStatusValidOnChainAfterPendingReceipt(chainTaskId, pretendedStatus);
        }

        return true;
    }

    private boolean isContributionStatusValidOnChain(final String chainTaskId, final ChainContributionStatus chainContributionStatus) {
        final Optional<ChainContribution> chainContribution = getChainContribution(chainTaskId);
        return chainContribution.isPresent() && chainContribution.get().getStatus() == chainContributionStatus;
    }

    private boolean isStatusValidOnChainAfterPendingReceipt(final String chainTaskId, final ChainContributionStatus onchainStatus) {
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

    Optional<ChainContribution> getChainContribution(final String chainTaskId) {
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

    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        return super.purgeTask(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        super.purgeAllTasksData();
    }

    synchronized TransactionReceipt submit(final String function, final String txData) {
        try {
            final BigInteger nonce = signerService.getNonce();
            final BigInteger gasLimit = "contributeAndFinalize".equals(function)
                    ? signerService.estimateGas(hubContractAddress, txData).add(getCallbackGas())
                    : PoCoDataEncoder.getGasLimitForFunction(function);
            return waitTxMined(signerService.signAndSendTransaction(
                    nonce, web3jService.getUserGasPrice(), gasLimit, hubContractAddress, txData));
        } catch (Exception e) {
            log.error("{} asynchronous execution did not complete", function, e);
        }
        // return non-null receipt with empty logs on failure
        final TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(List.of());
        return receipt;
    }

    TransactionReceipt waitTxMined(final String txHash) throws IOException, TransactionException {
        final TransactionReceipt receipt = txReceiptProcessor.waitForTransactionReceipt(txHash);
        log.info("Transaction receipt [hash:{}, status:{}, revert-reason:{}]",
                txHash, receipt.getStatus(), receipt.getRevertReason());
        if (receipt.isStatusOK()) {
            successCounter.increment();
        } else {
            failureCounter.increment();
        }
        return receipt;
    }
}
