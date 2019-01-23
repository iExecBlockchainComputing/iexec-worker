package com.iexec.worker.chain;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.result.ResultInfo;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class RevealService {

    private IexecHubService iexecHubService;
    private ResultService resultService;
    private CredentialsService credentialsService;

    public RevealService(IexecHubService iexecHubService,
                         ResultService resultService,
                         CredentialsService credentialsService) {
        this.iexecHubService = iexecHubService;
        this.resultService = resultService;
        this.credentialsService = credentialsService;
    }

    public boolean canReveal(String chainTaskId) {

        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChainTask.isPresent()) {
            return false;
        }
        ChainTask chainTask = optionalChainTask.get();

        boolean isChainTaskStatusRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);
        boolean isRevealDeadlineReached = chainTask.getRevealDeadline() < new Date().getTime();

        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTaskId);
        if (!optionalContribution.isPresent()) {
            return false;
        }
        ChainContribution chainContribution = optionalContribution.get();
        boolean isChainContributionStatusContributed = chainContribution.getStatus().equals(ChainContributionStatus.CONTRIBUTED);
        boolean isContributionResultHashConsensusValue = chainContribution.getResultHash().equals(chainTask.getConsensusValue());

        boolean isContributionResultHashCorrect = false;
        boolean isContributionResultSealCorrect = false;
        ResultInfo resultInfo = resultService.getResultInfo(chainTaskId);
        if (resultInfo != null && resultInfo.getDeterministHash() != null) {
            String deterministHash = resultInfo.getDeterministHash();
            isContributionResultHashCorrect = chainContribution.getResultHash().equals(HashUtils.concatenateAndHash(chainTaskId, deterministHash));

            String walletAddress = credentialsService.getCredentials().getAddress();
            isContributionResultSealCorrect = chainContribution.getResultSeal().equals(
                    HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash)
            );
        }

        boolean ret = isChainTaskStatusRevealing && !isRevealDeadlineReached &&
                isChainContributionStatusContributed && isContributionResultHashConsensusValue &&
                isContributionResultHashCorrect && isContributionResultSealCorrect;

        if (ret) {
            log.info("All the conditions are valid for the reveal to happen [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("One or more conditions are not met for the reveal to happen [chainTaskId:{}, " +
                            "isChainTaskStatusRevealing:{}, isRevealDeadlineReached:{}, " +
                            "isChainContributionStatusContributed:{}, isContributionResultHashConsensusValue:{}, " +
                            "isContributionResultHashCorrect:{}, isContributionResultSealCorrect:{}]", chainTaskId,
                    isChainTaskStatusRevealing, isRevealDeadlineReached,
                    isChainContributionStatusContributed, isContributionResultHashConsensusValue,
                    isContributionResultHashCorrect, isContributionResultSealCorrect);
        }

        return ret;
    }

    // returns the block number of the reveal if successful, 0 otherwise
    public long reveal(String chainTaskId) {
        long revealBlock = 0;
        ResultInfo resultInfo = resultService.getResultInfo(chainTaskId);
        if (resultInfo != null && resultInfo.getDeterministHash() != null) {
            String deterministHash = resultInfo.getDeterministHash();
            IexecHubABILegacy.TaskRevealEventResponse revealResponse = iexecHubService.reveal(chainTaskId, deterministHash);
            if (revealResponse != null) {
                // it seems response.log.getBlockNumber() could be null (issue in https://github.com/web3j/web3j should be opened)
                if (revealResponse.log.getBlockNumber() != null) {
                    revealBlock = revealResponse.log.getBlockNumber().longValue();
                } else {
                    log.error("RevealTransactionReceipt received but blockNumber is null inside [chainTaskId:{}, " +
                                    "receiptBlockNumber:{}, receiptLog:{}, block:{}]", chainTaskId,
                            revealResponse.log.getBlockNumber(), revealResponse.log.toString(), iexecHubService.getLastBlock());
                    revealBlock = -1;
                }
            }
        }
        return revealBlock;
    }

    public boolean hasEnoughGas() {
        return iexecHubService.hasEnoughGas();
    }
}
