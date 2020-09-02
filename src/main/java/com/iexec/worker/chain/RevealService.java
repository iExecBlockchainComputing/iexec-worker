/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubContract;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.worker.result.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class RevealService {

    private IexecHubService iexecHubService;
    private CredentialsService credentialsService;
    private Web3jService web3jService;

    public RevealService(IexecHubService iexecHubService,
                         CredentialsService credentialsService,
                         Web3jService web3jService) {
        this.iexecHubService = iexecHubService;
        this.credentialsService = credentialsService;
        this.web3jService = web3jService;
    }

    public boolean repeatCanReveal(String chainTaskId, String resultDigest) {
        return web3jService.repeatCheck(6, 3, "canReveal",
                this::canReveal, chainTaskId, resultDigest);
    }

    /*
     * params: String chainTaskId, String determinismHash
     * */
    public boolean canReveal(String... args) {
        String chainTaskId = args[0];
        String resultDigest = args[1];

        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChainTask.isPresent()) {
            log.error("Task couldn't be retrieved [chainTaskId:{}]", chainTaskId);
            return false;
        }
        ChainTask chainTask = optionalChainTask.get();

        boolean isChainTaskRevealing = iexecHubService.isChainTaskRevealing(chainTaskId);
        boolean isRevealDeadlineReached = chainTask.getRevealDeadline() < new Date().getTime();

        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTaskId);
        if (!optionalContribution.isPresent()) {
            log.error("Contribution couldn't be retrieved [chainTaskId:{}]", chainTaskId);
            return false;
        }
        ChainContribution chainContribution = optionalContribution.get();
        boolean isChainContributionStatusContributed = chainContribution.getStatus().equals(ChainContributionStatus.CONTRIBUTED);
        boolean isContributionResultHashConsensusValue = chainContribution.getResultHash().equals(chainTask.getConsensusValue());

        boolean isContributionResultHashCorrect = false;
        boolean isContributionResultSealCorrect = false;

        if (!resultDigest.isEmpty()) {//TODO
            isContributionResultHashCorrect = chainContribution.getResultHash().equals(ResultUtils.computeResultHash(chainTaskId, resultDigest));

            String walletAddress = credentialsService.getCredentials().getAddress();
            isContributionResultSealCorrect = chainContribution.getResultSeal().equals(
                    ResultUtils.computeResultSeal(walletAddress, chainTaskId, resultDigest)
            );
        }

        boolean ret = isChainTaskRevealing && !isRevealDeadlineReached &&
                isChainContributionStatusContributed && isContributionResultHashConsensusValue &&
                isContributionResultHashCorrect && isContributionResultSealCorrect;

        if (ret) {
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

        return ret;
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

        IexecHubContract.TaskRevealEventResponse revealResponse = iexecHubService.reveal(chainTaskId, resultDigest);
        if (revealResponse == null) {
            log.error("RevealTransactionReceipt received but was null [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(revealResponse.log,
                chainTaskId, iexecHubService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }
}
