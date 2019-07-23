package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;

import com.iexec.worker.config.PublicConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;


@Slf4j
@Service
public class ContributionService {

    private IexecHubService iexecHubService;
    private ContributionAuthorizationService contributionAuthorizationService;

    public ContributionService(IexecHubService iexecHubService,
                               ContributionAuthorizationService contributionAuthorizationService) {
        this.iexecHubService = iexecHubService;
        this.contributionAuthorizationService = contributionAuthorizationService;
    }

    public static String computeResultSeal(String walletAddress, String chainTaskId, String deterministHash) {
        return HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);
    }

    public static String computeResultHash(String chainTaskId, String deterministHash) {
        return HashUtils.concatenateAndHash(chainTaskId, deterministHash);
    }

    public boolean isChainTaskInitialized(String chainTaskId) {
        return iexecHubService.getChainTask(chainTaskId).isPresent();
    }

    public Optional<ReplicateStatusCause> getCannotContributeStatusCause(String chainTaskId) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChainTask.isPresent()) {
            return Optional.of(CHAIN_UNREACHABLE);
        }

        ChainTask chainTask = optionalChainTask.get();

        if (!hasEnoughStakeToContribute(chainTask)) {
            return Optional.of(STAKE_TOO_LOW);
        }

        if (!isTaskActiveToContribute(chainTask)) {
            return Optional.of(TASK_NOT_ACTIVE);
        }

        if (!isBeforeContributionDeadlineToContribute(chainTask)) {
            return Optional.of(AFTER_DEADLINE);
        }

        if (!isContributionUnsetToContribute(chainTask)) {
            return Optional.of(CONTRIBUTION_ALREADY_SET);
        }

        if (!isContributionAuthorizationPresent(chainTaskId)) {
            return Optional.of(CONTRIBUTION_AUTHORIZATION_MISSING);
        }

        return Optional.empty();
    }

    private boolean isContributionAuthorizationPresent(String chainTaskId) {
        ContributionAuthorization contributionAuthorization =
                contributionAuthorizationService.getContributionAuthorization(chainTaskId);
        if (contributionAuthorization != null){
            return true;
        }
        log.error("ContributionAuthorization missing [chainTaskId:{}]", chainTaskId);
        return false;
    }

    private boolean hasEnoughStakeToContribute(ChainTask chainTask) {
        Optional<ChainAccount> optionalChainAccount = iexecHubService.getChainAccount();
        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainAccount.isPresent() || !optionalChainDeal.isPresent()) {
            return false;
        }
        return optionalChainAccount.get().getDeposit() >= optionalChainDeal.get().getWorkerStake().longValue();
    }

    private boolean isTaskActiveToContribute(ChainTask chainTask) {
        return iexecHubService.isChainTaskActive(chainTask.getChainTaskId());
    }

    private boolean isBeforeContributionDeadlineToContribute(ChainTask chainTask) {
        return new Date().getTime() < chainTask.getContributionDeadline();
    }

    private boolean isContributionUnsetToContribute(ChainTask chainTask) {
        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTask.getChainTaskId());
        if (!optionalContribution.isPresent()) return false;

        ChainContribution chainContribution = optionalContribution.get();
        return chainContribution.getStatus().equals(ChainContributionStatus.UNSET);
    }

    public boolean isContributionAuthorizationValid(ContributionAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] message = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclaveChallenge()));

        return SignatureUtils.isSignatureValid(message, auth.getSignature(), signerAddress);
    }

    public boolean isContributionDeadlineReached(String chainTaskId) {
        Optional<ChainTask> oTask = iexecHubService.getChainTask(chainTaskId);
        if (!oTask.isPresent()) return true;

        return !isBeforeContributionDeadlineToContribute(oTask.get());
    }

    // returns ChainReceipt of the contribution if successful, null otherwise
    public Optional<ChainReceipt> contribute(ContributionAuthorization contribAuth, String deterministHash, Signature enclaveSignature) {
        String resultSeal = computeResultSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash);
        String resultHash = computeResultHash(contribAuth.getChainTaskId(), deterministHash);
        IexecHubABILegacy.TaskContributeEventResponse contributeResponse = iexecHubService.contribute(contribAuth, resultHash, resultSeal, enclaveSignature);

        if (contributeResponse == null) {
            log.error("ContributeTransactionReceipt received but was null [chainTaskId:{}]", contribAuth.getChainTaskId());
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(contributeResponse.log, contribAuth.getChainTaskId(),
                iexecHubService.getLatestBlockNumber());



        return Optional.of(chainReceipt);
    }

    public boolean putContributionAuthorization(ContributionAuthorization contributionAuthorization) {
        return contributionAuthorizationService.putContributionAuthorization(contributionAuthorization);
    }

    public ContributionAuthorization getContributionAuthorization(String chainTaskId) {
        return contributionAuthorizationService.getContributionAuthorization(chainTaskId);
    }

}
