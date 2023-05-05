/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.worker.config.BlockchainAdapterConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.iexec.commons.poco.chain.ChainContributionStatus.CONTRIBUTED;
import static com.iexec.commons.poco.chain.ChainContributionStatus.REVEALED;
import static com.iexec.commons.poco.utils.BytesUtils.bytesToString;
import static com.iexec.commons.poco.utils.BytesUtils.stringToBytes;


@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService {

    private final CredentialsService credentialsService;
    private final ThreadPoolExecutor executor;
    private final Web3jService web3jService;
    private final Integer chainId;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           BlockchainAdapterConfigurationService blockchainAdapterConfigurationService) {
        super(credentialsService.getCredentials(),
                web3jService,
                blockchainAdapterConfigurationService.getIexecHubContractAddress(),
                blockchainAdapterConfigurationService.getBlockTime(),
                1,
                5);
        this.credentialsService = credentialsService;
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        this.chainId = blockchainAdapterConfigurationService.getChainId();
    }

    IexecHubContract.TaskContributeEventResponse contribute(Contribution contribution) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Requested contribute [chainTaskId:{}, waitingTxCount:{}]",
                        contribution.getChainTaskId(), getWaitingTransactionCount());
                return sendContributeTransaction(contribution);
            }, executor).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("contribute asynchronous execution did not complete", e);
        }
        return null;
    }

    private IexecHubContract.TaskContributeEventResponse sendContributeTransaction(Contribution contribution) {
        TransactionReceipt contributeReceipt;
        String chainTaskId = contribution.getChainTaskId();

        RemoteCall<TransactionReceipt> contributeCall = getWriteableHubContract().contribute(
                stringToBytes(chainTaskId),
                stringToBytes(contribution.getResultHash()),
                stringToBytes(contribution.getResultSeal()),
                contribution.getEnclaveChallenge(),
                stringToBytes(contribution.getEnclaveSignature()),
                stringToBytes(contribution.getWorkerPoolSignature()));
        log.info("Sent contribute [chainTaskId:{}, contribution:{}]", chainTaskId, contribution);

        try {
            contributeReceipt = contributeCall.send();
        } catch (Exception e) {
            log.error("Failed to contribute [chainTaskId:{}]", chainTaskId, e);
            return null;
        }

        List<IexecHubContract.TaskContributeEventResponse> contributeEvents =
                getHubContract().getTaskContributeEvents(contributeReceipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId)
                                && Objects.equals(event.worker, credentialsService.getCredentials().getAddress()))
                        .collect(Collectors.toList());
        log.debug("contributeEvents count {} [chainTaskId: {}]", contributeEvents.size(), chainTaskId);

        IexecHubContract.TaskContributeEventResponse contributeEvent = null;
        if (!contributeEvents.isEmpty()) {
            contributeEvent = contributeEvents.get(0);
        }

        if (isSuccessTx(chainTaskId, contributeEvent, CONTRIBUTED)) {
            log.info("Contributed [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                    chainTaskId, contribution, contributeReceipt.getGasUsed(), contributeEvent.log);
            return contributeEvent;
        }

        log.error("Failed to contribute [chainTaskId:{}]", chainTaskId);
        return null;
    }

    private IexecHubContract getWriteableHubContract() {
        return getHubContract(web3jService.getWritingContractGasProvider(), chainId);
    }

    private boolean isSuccessTx(String chainTaskId, BaseEventResponse txEvent, ChainContributionStatus pretendedStatus) {
        if (txEvent == null || txEvent.log == null) {
            return false;
        }

        if (txEvent.log.getType() == null || txEvent.log.getType().equals(PENDING_RECEIPT_STATUS)) {
            return isStatusValidOnChainAfterPendingReceipt(chainTaskId, pretendedStatus, this::isContributionStatusValidOnChain);
        }

        return true;
    }

    IexecHubContract.TaskRevealEventResponse reveal(String chainTaskId, String resultDigest) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Requested reveal [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
                return sendRevealTransaction(chainTaskId, resultDigest);
            }, executor).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("reveal asynchronous execution did not complete", e);
        }
        return null;
    }

    private IexecHubContract.TaskRevealEventResponse sendRevealTransaction(String chainTaskId, String resultDigest) {
        TransactionReceipt revealReceipt;
        RemoteCall<TransactionReceipt> revealCall = getWriteableHubContract().reveal(
                stringToBytes(chainTaskId),
                stringToBytes(resultDigest));
        log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);

        try {
            revealReceipt = revealCall.send();
        } catch (Exception e) {
            log.error("Failed to reveal [chainTaskId:{}]", chainTaskId, e);
            return null;
        }

        List<IexecHubContract.TaskRevealEventResponse> revealEvents =
                getHubContract().getTaskRevealEvents(revealReceipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId)
                                && Objects.equals(event.worker, credentialsService.getCredentials().getAddress()))
                        .collect(Collectors.toList());
        log.debug("revealEvents count {} [chainTaskId:{}]", revealEvents.size(), chainTaskId);

        IexecHubContract.TaskRevealEventResponse revealEvent = null;
        if (!revealEvents.isEmpty()) {
            revealEvent = revealEvents.get(0);
        }

        if (isSuccessTx(chainTaskId, revealEvent, REVEALED)) {
            log.info("Revealed [chainTaskId:{}, resultDigest:{}, gasUsed:{}, log:{}]",
                    chainTaskId, resultDigest, revealReceipt.getGasUsed(), revealEvent.log);
            return revealEvent;
        }

        log.error("Failed to reveal [chainTaskId:{}]", chainTaskId);
        return null;
    }

    public Optional<ChainReceipt> contributeAndFinalize(Contribution contribution, String resultLink, String callbackData) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("contributeAndFinalize request [chainTaskId:{}, waitingTxCount:{}]",
                        contribution.getChainTaskId(), getWaitingTransactionCount());
                IexecHubContract.TaskFinalizeEventResponse finalizeEvent = sendContributeAndFinalizeTransaction(contribution, resultLink, callbackData);
                return Optional.ofNullable(finalizeEvent)
                        .map(event -> ChainUtils.buildChainReceipt(event.log, contribution.getChainTaskId(), getLatestBlockNumber()));
            }, executor).get();
        } catch (ExecutionException e) {
            log.error("contributeAndFinalize asynchronous execution did not complete", e);
        } catch (InterruptedException e) {
            log.error("contributeAndFinalize thread has been interrupted", e);
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private IexecHubContract.TaskFinalizeEventResponse sendContributeAndFinalizeTransaction(Contribution contribution, String resultLink, String callbackData) {
        TransactionReceipt receipt;
        String chainTaskId = contribution.getChainTaskId();

        RemoteCall<TransactionReceipt> contributeAndFinalizeCall = getWriteableHubContract().contributeAndFinalize(
                stringToBytes(chainTaskId),
                stringToBytes(contribution.getResultDigest()),
                StringUtils.isNotEmpty(resultLink) ? resultLink.getBytes(StandardCharsets.UTF_8) : new byte[0],
                StringUtils.isNotEmpty(callbackData) ? stringToBytes(callbackData) : new byte[0],
                contribution.getEnclaveChallenge(),
                stringToBytes(contribution.getEnclaveSignature()),
                stringToBytes(contribution.getWorkerPoolSignature()));
        log.info("Sent contributeAndFinalize [chainTaskId:{}, contribution:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, contribution, resultLink, callbackData);

        try {
            receipt = contributeAndFinalizeCall.send();
            log.debug("Transaction hash {} at block {} [chainTaskId:{}]", receipt.getTransactionHash(), receipt.getBlockNumber(), chainTaskId);
        } catch (Exception e) {
            log.error("contributeAndFinalize failed [chainTaskId:{}]", chainTaskId, e);
            return null;
        }

        List<IexecHubContract.TaskFinalizeEventResponse> finalizeEvents =
                getHubContract().getTaskFinalizeEvents(receipt).stream()
                        .filter(event -> Objects.equals(bytesToString(event.taskid), chainTaskId))
                        .collect(Collectors.toList());
        log.debug("finalizeEvents count {} [chainTaskId:{}]", finalizeEvents.size(), chainTaskId);

        IexecHubContract.TaskFinalizeEventResponse finalizeEvent = null;
        if (!finalizeEvents.isEmpty()) {
            finalizeEvent = finalizeEvents.get(0);
        }

        if (isSuccessTx(chainTaskId, finalizeEvent, REVEALED)) {
            log.info("contributeAndFinalize done [chainTaskId:{}, contribution:{}, gasUsed:{}, log:{}]",
                    chainTaskId, contribution, receipt.getGasUsed(), finalizeEvent.log);
            return finalizeEvent;
        }

        log.error("contributeAndFinalize failed [chainTaskId:{}]", chainTaskId);
        return null;
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - 1 - executor.getCompletedTaskCount();
    }

    Optional<ChainContribution> getChainContribution(String chainTaskId) {
        return getChainContribution(chainTaskId, credentialsService.getCredentials().getAddress());
    }

    Optional<ChainAccount> getChainAccount() {
        return getChainAccount(credentialsService.getCredentials().getAddress());
    }

    public boolean hasEnoughGas() {
        return web3jService.hasEnoughGas(credentialsService.getCredentials().getAddress());
    }

    public long getLatestBlockNumber() {
        return web3jService.getLatestBlockNumber();
    }

    public long getMaxWaitingTimeWhenNotSync() {
        return web3jService.getMaxWaitingTimeWhenPendingReceipt();
    }

    private Boolean isContributionStatusValidOnChain(String chainTaskId, ChainStatus chainContributionStatus) {
        if (chainContributionStatus instanceof ChainContributionStatus) {
            Optional<ChainContribution> chainContribution = getChainContribution(chainTaskId);
            return chainContribution.isPresent() && chainContribution.get().getStatus().equals(chainContributionStatus);
        }
        return false;
    }

    private boolean isBlockchainReadTrueWhenNodeNotSync(String chainTaskId, Function<String, Boolean> booleanBlockchainReadFunction) {
        long maxWaitingTime = web3jService.getMaxWaitingTimeWhenPendingReceipt();
        long startTime = System.currentTimeMillis();

        for (long duration = 0L; duration < maxWaitingTime; duration = System.currentTimeMillis() - startTime) {
            try {
                if (booleanBlockchainReadFunction.apply(chainTaskId)) {
                    return true;
                }

                Thread.sleep(500L);
            } catch (InterruptedException e) {
                log.error("Error in checking the latest block number [chainTaskId:{}, maxWaitingTime:{}]",
                        chainTaskId, maxWaitingTime);
            }
        }

        return false;
    }

    Boolean isChainTaskActive(String chainTaskId) {
        Optional<ChainTask> chainTask = getChainTask(chainTaskId);
        if (chainTask.isPresent()) {
            switch (chainTask.get().getStatus()) {
                case UNSET:
                    break;//Could happen if node not synchronized. Should wait.
                case ACTIVE:
                    return true;
                case REVEALING:
                    return false;
                case COMPLETED:
                    return false;
                case FAILLED:
                    return false;
            }
        }
        return false;
    }

    public Boolean isChainTaskRevealing(String chainTaskId) {
        Optional<ChainTask> chainTask = getChainTask(chainTaskId);
        if (chainTask.isPresent()) {
            switch (chainTask.get().getStatus()) {
                case UNSET:
                    break;//Should not happen
                case ACTIVE:
                    break;//Could happen if node not synchronized. Should wait.
                case REVEALING:
                    return true;
                case COMPLETED:
                    return false;
                case FAILLED:
                    return false;
            }
        }
        return false;
    }
}
