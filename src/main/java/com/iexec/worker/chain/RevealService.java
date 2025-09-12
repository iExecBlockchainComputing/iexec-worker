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

import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class RevealService {

    private final IexecHubService iexecHubService;
    private final Web3jService web3jService;
    private final String workerWalletAddress;

    public RevealService(IexecHubService iexecHubService,
                         Web3jService web3jService,
                         String workerWalletAddress) {
        this.iexecHubService = iexecHubService;
        this.web3jService = web3jService;
        this.workerWalletAddress = workerWalletAddress;
    }

    public boolean repeatCanReveal(String chainTaskId, String resultDigest) {
        return web3jService.repeatCheck(6, 3, "canReveal",
                this::canReveal, chainTaskId, resultDigest);
    }

    /*
     * params: String chainTaskId, String determinismHash
     * */
    public boolean canReveal(String... args) {
        final String chainTaskId = args[0];
        final String resultDigest = args[1];

        // fail fast when result digest is invalid, should not happen with check in TaskManagerService#reveal
        if (resultDigest.isEmpty()) {
            log.error("Result digest not found, impossible to compute result hash or result seal [chainTaskId:{}]",
                    chainTaskId);
            return false;
        }

        final ChainTask chainTask = iexecHubService.getChainTask(chainTaskId).orElse(null);
        if (chainTask == null) {
            log.error("Task couldn't be retrieved [chainTaskId:{}]", chainTaskId);
            return false;
        }

        final boolean isChainTaskRevealing = chainTask.getStatus() == ChainTaskStatus.REVEALING;
        final boolean isRevealDeadlineReached = chainTask.isRevealDeadlineReached();

        final ChainContribution chainContribution = iexecHubService.getChainContribution(chainTaskId).orElse(null);
        if (chainContribution == null) {
            log.error("Contribution couldn't be retrieved [chainTaskId:{}]", chainTaskId);
            return false;
        }
        final boolean isChainContributionStatusContributed = chainContribution.getStatus() == ChainContributionStatus.CONTRIBUTED;
        final boolean isContributionResultHashConsensusValue = Objects.equals(chainContribution.getResultHash(), chainTask.getConsensusValue());

        final boolean isContributionResultHashCorrect = Objects.equals(
                chainContribution.getResultHash(), HashUtils.concatenateAndHash(chainTaskId, resultDigest));
        final boolean isContributionResultSealCorrect = Objects.equals(
                chainContribution.getResultSeal(), HashUtils.concatenateAndHash(workerWalletAddress, chainTaskId, resultDigest));

        final boolean canReveal = isChainTaskRevealing && !isRevealDeadlineReached &&
                isChainContributionStatusContributed && isContributionResultHashConsensusValue &&
                isContributionResultHashCorrect && isContributionResultSealCorrect;

        if (canReveal) {
            log.info("All the conditions are valid for the reveal to happen [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("One or more conditions are not met for the reveal to happen [chainTaskId:{}, " +
                            "isChainTaskRevealing:{}, isRevealDeadlineReached:{}, " +
                            "isChainContributionStatusContributed:{}, isContributionResultHashConsensusValue:{}, " +
                            "isContributionResultHashCorrect:{}, isContributionResultSealCorrect:{}]", chainTaskId,
                    isChainTaskRevealing, isRevealDeadlineReached,
                    isChainContributionStatusContributed, isContributionResultHashConsensusValue,
                    isContributionResultHashCorrect, isContributionResultSealCorrect);
        }

        return canReveal;
    }

    public boolean isConsensusBlockReached(String chainTaskId, long consensusBlock) {
        if (web3jService.isBlockAvailable(consensusBlock)) return true;

        log.warn("Chain sync issues, consensus block not reached yet [chainTaskId:{}, latestBlock:{}, consensusBlock:{}]",
                chainTaskId, web3jService.getLatestBlockNumber(), consensusBlock);
        return false;
    }

    // returns the ChainReceipt of the reveal if successful, empty otherwise
    public Optional<ChainReceipt> reveal(String chainTaskId, String resultDigest) {

        if (resultDigest.isEmpty()) {
            return Optional.empty();
        }

        final Log revealResponse = iexecHubService.reveal(chainTaskId, resultDigest);
        if (revealResponse == null) {
            log.error("RevealTransactionReceipt received but was null [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(revealResponse,
                chainTaskId, iexecHubService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }
}
