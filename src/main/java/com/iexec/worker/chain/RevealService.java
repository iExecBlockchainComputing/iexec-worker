package com.iexec.worker.chain;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

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
        MetadataResult metadataResult = resultService.getMetaDataResult(chainTaskId);
        if (metadataResult != null && metadataResult.getDeterministHash() != null) {
            String deterministHash = metadataResult.getDeterministHash();
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

    public boolean reveal(String chainTaskId){
        MetadataResult metadataResult = resultService.getMetaDataResult(chainTaskId);
        if (metadataResult != null && metadataResult.getDeterministHash() != null) {
            String deterministHash = metadataResult.getDeterministHash();
            try {
                return iexecHubService.reveal(chainTaskId, deterministHash) != null;
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}
