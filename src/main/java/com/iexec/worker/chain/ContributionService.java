package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.security.TeeSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ContributionService {

    private IexecHubService iexecHubService;

    public ContributionService(IexecHubService iexecHubService) {
        this.iexecHubService = iexecHubService;
    }

    public boolean isChainTaskInitialized(String chainTaskId) {
        return iexecHubService.getChainTask(chainTaskId).isPresent();
    }

    public boolean canContribute(String chainTaskId) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChainTask.isPresent()) {
            return false;
        }
        ChainTask chainTask = optionalChainTask.get();

        Optional<ChainAccount> optionalChainAccount = iexecHubService.getChainAccount();
        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainAccount.isPresent() || !optionalChainDeal.isPresent()) {
            return false;
        }
        if (optionalChainAccount.get().getDeposit() < optionalChainDeal.get().getWorkerStake().longValue()) {
            log.error("Stake to low to contribute [chainTaskId:{}, current:{}, required:{}]",
                    chainTaskId, optionalChainAccount.get().getDeposit(),
                    optionalChainDeal.get().getWorkerStake().longValue());
            return false;
        }


        boolean isTaskActive = chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);
        boolean willNeverBeAbleToContribute = chainTask.getStatus().equals(ChainTaskStatus.REVEALING)
                || chainTask.getStatus().equals(ChainTaskStatus.COMPLETED)
                || chainTask.getStatus().equals(ChainTaskStatus.FAILLED);

        boolean contributionDeadlineReached = chainTask.getContributionDeadline() < new Date().getTime();

        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTaskId);
        if (!optionalContribution.isPresent()) {
            return false;
        }
        ChainContribution chainContribution = optionalContribution.get();
        boolean isContributionUnset = chainContribution.getStatus().equals(ChainContributionStatus.UNSET);

        if (isTaskActive && !contributionDeadlineReached && isContributionUnset && !willNeverBeAbleToContribute) {
            log.info("Can contribute [chainTaskId:{}]", chainTaskId);

            return true;
        } else {
            log.warn("Can't contribute [chainTaskId:{}, isTaskActive:{}, contributionDeadlineReached:{}, " +
                            "isContributionUnset:{}, chainTaskStatus:{}], willNeverBeAbleToContribute:{}",
                    chainTaskId, isTaskActive, contributionDeadlineReached, isContributionUnset, chainTask.getStatus(),
                    willNeverBeAbleToContribute);
            return false;
        }

    }

    // returns the block number of the contribution if successful, 0 otherwise
    public long contribute(ContributionAuthorization contribAuth, String deterministHash, Optional<TeeSignature.Sign> optionalEnclaveSignature) {
        String resultSeal = computeResultSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash);
        String resultHash = computeResultHash(contribAuth.getChainTaskId(), deterministHash);
        try {
            IexecHubABILegacy.TaskContributeEventResponse response = iexecHubService.contribute(contribAuth, resultHash, resultSeal, optionalEnclaveSignature);
            return response.log.getBlockNumber().longValue();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String computeResultSeal(String walletAddress, String chainTaskId, String deterministHash) {
        return HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);
    }

    public static String computeResultHash(String chainTaskId, String deterministHash) {
        return HashUtils.concatenateAndHash(chainTaskId, deterministHash);
    }

    public boolean isContributionAuthorizationValid(ContributionAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] hash = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclave()));
        byte[] hashTocheck = SignatureUtils.getEthereumMessageHash(hash);

        return SignatureUtils.doesSignatureMatchesAddress(auth.getSignR(), auth.getSignS(),
                BytesUtils.bytesToString(hashTocheck), signerAddress);
    }

    public boolean isEnclaveSignatureValid(String resulHash, String resultSeal, TeeSignature.Sign enclaveSignature, String signerAddress) {
        byte[] hash = BytesUtils.stringToBytes(HashUtils.concatenateAndHash(resulHash, resultSeal));
        byte[] hashTocheck = SignatureUtils.getEthereumMessageHash(hash);

        return SignatureUtils.doesSignatureMatchesAddress(BytesUtils.stringToBytes(enclaveSignature.getR()), BytesUtils.stringToBytes(enclaveSignature.getS()),
                BytesUtils.bytesToString(hashTocheck), signerAddress.toLowerCase());
    }


    public boolean hasEnoughGas() {
        return iexecHubService.hasEnoughGas();
    }
}
