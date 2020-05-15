package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubContract;
import com.iexec.common.contribution.Contribution;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.security.Signature;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;

import com.iexec.common.worker.result.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.common.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;


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
            return Optional.of(CONTRIBUTION_TIMEOUT);
        }

        if (!isContributionUnsetToContribute(chainTask)) {
            return Optional.of(CONTRIBUTION_ALREADY_SET);
        }

        if (!isContributionAuthorizationPresent(chainTaskId)) {
            return Optional.of(CONTRIBUTION_AUTHORIZATION_NOT_FOUND);
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
    public Optional<ChainReceipt> contribute(Contribution contribution) {

        IexecHubContract.TaskContributeEventResponse contributeResponse = iexecHubService.contribute(contribution);

        if (contributeResponse == null) {
            log.error("ContributeTransactionReceipt received but was null [chainTaskId:{}]", contribution.getChainTaskId());
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(contributeResponse.log, contribution.getChainTaskId(),
                iexecHubService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }

    public boolean putContributionAuthorization(ContributionAuthorization contributionAuthorization) {
        return contributionAuthorizationService.putContributionAuthorization(contributionAuthorization);
    }

    public ContributionAuthorization getContributionAuthorization(String chainTaskId) {
        return contributionAuthorizationService.getContributionAuthorization(chainTaskId);
    }

    //TODO Add unit test
    public Contribution getContribution(ComputedFile computedFile, ContributionAuthorization contributionAuthorization) {
        String chainTaskId = computedFile.getTaskId();
        String resultDigest = computedFile.getResultDigest();
        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(contributionAuthorization.getWorkerWallet(), chainTaskId, resultDigest);
        String enclaveSignature = computedFile.getEnclaveSignature();

        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);
        if (isTeeTask) {
            if (enclaveSignature.isEmpty()){
                log.error("Cannot contribute enclave signature not found [chainTaskId:{}]", chainTaskId);
                return null;
            }

            String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);

            boolean isExpectedSigner = isExpectedSignerOnSignedMessageHash(messageHash,
                    new Signature(enclaveSignature), contributionAuthorization.getEnclaveChallenge());

            if (!isExpectedSigner){
                log.error("Cannot contribute enclave signature invalid [chainTaskId:{}]", chainTaskId);
                return null;
            }

        } else {
            enclaveSignature = BytesUtils.EMPTY_HEXASTRING_64;
        }

        return Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(contributionAuthorization.getEnclaveChallenge())
                .enclaveSignature(enclaveSignature)
                .workerPoolSignature(contributionAuthorization.getSignature().getValue())
                .build();
    }

}
