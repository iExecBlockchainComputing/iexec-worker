package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.result.ResultInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;

import java.util.Date;
import java.util.Optional;

import static com.iexec.common.utils.BytesUtils.*;

@Slf4j
@Service
public class ContributionService {

    private IexecHubService iexecHubService;

    public ContributionService(IexecHubService iexecHubService) {
        this.iexecHubService = iexecHubService;
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

    /*
     * If TEE tag missing :              return empty enclaveSignature
     * If TEE tag present :              return proper enclaveSignature
     * If TEE tag present but problem :  return null
     * */
    public Sign.SignatureData getEnclaveSignatureData(ContributionAuthorization contribAuth, ResultInfo resultInfo) {
        Sign.SignatureData enclaveSignatureData;
        if (!(contribAuth.getEnclave().equals(EMPTY_ADDRESS) || contribAuth.getEnclave().isEmpty())) {
            if (!resultInfo.getEnclaveSignature().isPresent()) {
                log.info("Can't contribute (enclaveChalenge is set but enclaveSignature missing) [chainTaskId:{]", contribAuth.getChainTaskId());
                return null;
            }

            enclaveSignatureData = new Sign.SignatureData(
                    resultInfo.getEnclaveSignature().get().getV().byteValue(),
                    stringToBytes(resultInfo.getEnclaveSignature().get().getR()),
                    stringToBytes(resultInfo.getEnclaveSignature().get().getS())
            );

            String resultSeal = computeResultSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), resultInfo.getDeterministHash());
            String resultHash = computeResultHash(contribAuth.getChainTaskId(), resultInfo.getDeterministHash());
            boolean isEnclaveSignatureValid = isEnclaveSignatureValid(resultHash, resultSeal,
                    enclaveSignatureData, contribAuth.getEnclave());

            if (!isEnclaveSignatureValid) {
                log.error("Can't contribute (enclaveChalenge is set but enclaveSignature not valid) [chainTaskId:{}, " +
                        "isEnclaveSignatureValid:{}]", contribAuth.getChainTaskId(), isEnclaveSignatureValid);
                return null;
            }

            return enclaveSignatureData;
        }

        return new Sign.SignatureData(
                new Integer(0).byteValue(),
                stringToBytes(EMPTY_HEXASTRING_64),
                stringToBytes(EMPTY_HEXASTRING_64)
        );
    }

    // returns the block number of the contribution if successful, 0 otherwise
    public long contribute(ContributionAuthorization contribAuth, String deterministHash, Sign.SignatureData enclaveSignatureData) {
        long contributeBlock = 0;
        String resultSeal = computeResultSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash);
        String resultHash = computeResultHash(contribAuth.getChainTaskId(), deterministHash);
        IexecHubABILegacy.TaskContributeEventResponse contributeResponse = iexecHubService.contribute(contribAuth, resultHash, resultSeal, enclaveSignatureData);
        if (contributeResponse != null) {
            // it seems response.log.getBlockNumber() could be null (issue in https://github.com/web3j/web3j should be opened)
            if (contributeResponse.log.getBlockNumber() != null) {
                contributeBlock = contributeResponse.log.getBlockNumber().longValue();
            } else {
                log.error("ContributeTransactionReceipt received but blockNumber is null inside [chainTaskId:{}, " +
                        "receiptBlockNumber:{}, receiptLog:{}, block:{}]", contribAuth.getChainTaskId(),
                        contributeResponse.log.getBlockNumber(), contributeResponse.log.toString(), iexecHubService.getLastBlock());
                contributeBlock = -1;
            }
        }
        return contributeBlock;
    }

    public boolean isContributionAuthorizationValid(ContributionAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] hash = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclave()));
        byte[] hashTocheck = SignatureUtils.getEthereumMessageHash(hash);

        return SignatureUtils.doesSignatureMatchesAddress(auth.getSignR(), auth.getSignS(),
                BytesUtils.bytesToString(hashTocheck), signerAddress);
    }

    public boolean isEnclaveSignatureValid(String resulHash, String resultSeal, Sign.SignatureData enclaveSignature, String signerAddress) {
        byte[] hash = BytesUtils.stringToBytes(HashUtils.concatenateAndHash(resulHash, resultSeal));
        byte[] hashTocheck = SignatureUtils.getEthereumMessageHash(hash);

        return SignatureUtils.doesSignatureMatchesAddress(enclaveSignature.getR(), enclaveSignature.getS(),
                BytesUtils.bytesToString(hashTocheck), signerAddress.toLowerCase());
    }


    public boolean hasEnoughGas() {
        return iexecHubService.hasEnoughGas();
    }
}
