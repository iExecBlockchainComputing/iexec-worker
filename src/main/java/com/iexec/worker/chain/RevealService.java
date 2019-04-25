package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.HashUtils;
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
        String deterministHash = resultService.getDeterministHashFromFile(chainTaskId);
        if (!deterministHash.isEmpty()) {
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

    // returns the ChainReceipt of the reveal if successful, null otherwise
    public Optional<ChainReceipt> reveal(String chainTaskId) {
        String deterministHash = resultService.getDeterministHashFromFile(chainTaskId);

        if (deterministHash.isEmpty()) {
            return Optional.empty();
        }

        IexecHubABILegacy.TaskRevealEventResponse revealResponse = iexecHubService.reveal(chainTaskId, deterministHash);

        if (revealResponse == null) {
            log.error("RevealTransactionReceipt received but was null [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(revealResponse.log,
                chainTaskId, iexecHubService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }

    public boolean hasEnoughGas() {
        return iexecHubService.hasEnoughGas();
    }
}
