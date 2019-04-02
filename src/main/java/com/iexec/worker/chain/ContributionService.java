package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.security.SignatureException;
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

    public Optional<ReplicateStatus> getCanContributeStatus(String chainTaskId) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChainTask.isPresent()) {
            return Optional.empty();
        }
        ChainTask chainTask = optionalChainTask.get();

        if (!hasEnoughtStakeToContribute(chainTask)) {
            return Optional.of(ReplicateStatus.CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW);
        }

        if (!isTaskActiveToContribute(chainTask)) {
            return Optional.of(ReplicateStatus.CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE);
        }

        if (!isBeforeContributionDeadlineToContribute(chainTask)) {
            return Optional.of(ReplicateStatus.CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE);
        }

        if (!isContributionUnsetToContribute(chainTask)) {
            return Optional.of(ReplicateStatus.CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET);
        }

        return Optional.of(ReplicateStatus.CAN_CONTRIBUTE);
    }


    private boolean hasEnoughtStakeToContribute(ChainTask chainTask) {
        Optional<ChainAccount> optionalChainAccount = iexecHubService.getChainAccount();
        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainAccount.isPresent() || !optionalChainDeal.isPresent()) {
            return false;
        }
        return optionalChainAccount.get().getDeposit() >= optionalChainDeal.get().getWorkerStake().longValue();
    }

    private boolean isTaskActiveToContribute(ChainTask chainTask) {
        return chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);
    }

    private boolean isBeforeContributionDeadlineToContribute(ChainTask chainTask) {
        return new Date().getTime() < chainTask.getContributionDeadline();
    }

    private boolean isContributionUnsetToContribute(ChainTask chainTask) {
        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTask.getChainTaskId());
        if (!optionalContribution.isPresent()) {
            return false;
        }
        ChainContribution chainContribution = optionalContribution.get();
        return chainContribution.getStatus().equals(ChainContributionStatus.UNSET);
    }

    /*
     * If TEE tag missing :              return empty enclaveSignature
     * If TEE tag present :              return proper enclaveSignature
     * If TEE tag present but problem :  return null
     * */
    public Signature getEnclaveSignature(ContributionAuthorization contribAuth, String deterministHash, Signature enclaveSignature) {

        if (contribAuth.getEnclave().equals(EMPTY_ADDRESS) || contribAuth.getEnclave().isEmpty()) {
            return SignatureUtils.emptySignature();
        }

        if (enclaveSignature == null ) {
            log.info("Can't contribute (enclaveChalenge is set but enclaveSignature missing) [chainTaskId:{]", contribAuth.getChainTaskId());
            return null;
        }

        String resultSeal = computeResultSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash);
        String resultHash = computeResultHash(contribAuth.getChainTaskId(), deterministHash);
        boolean isEnclaveSignatureValid = isEnclaveSignatureValid(resultHash, resultSeal,
                enclaveSignature, contribAuth.getEnclave());

        if (!isEnclaveSignatureValid) {
            log.error("Can't contribute (enclaveChalenge is set but enclaveSignature not valid) [chainTaskId:{}, " +
                    "isEnclaveSignatureValid:{}]", contribAuth.getChainTaskId(), isEnclaveSignatureValid);
            return null;
        }

        return enclaveSignature;

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
                iexecHubService.getLastBlockNumber());

        return Optional.of(chainReceipt);
    }

    public boolean isContributionAuthorizationValid(ContributionAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] message = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclave()));

        return isSignatureValid(message, auth.getSignature(), signerAddress);
    }

    public boolean isEnclaveSignatureValid(String resultHash, String resultSeal, Signature enclaveSignature, String signerAddress) {
        byte[] message = BytesUtils.stringToBytes(HashUtils.concatenateAndHash(resultHash, resultSeal));

        return isSignatureValid(message, enclaveSignature, signerAddress);
    }

    private boolean isSignatureValid(byte[] message, Signature sign, String signerAddress) {
        try {
            Sign.SignatureData signatureData = new Sign.SignatureData(sign.getV(), sign.getR(), sign.getS());

            BigInteger publicKey = Sign.signedPrefixedMessageToKey(message, signatureData);
            if (publicKey != null) {
                String addressRecovered = "0x" + Keys.getAddress(publicKey);
                return addressRecovered.equalsIgnoreCase(signerAddress);
            }

        } catch (SignatureException e) {
            log.error("Signature exception [exception:{}]", e.toString());
        }
        return false;
    }

    public boolean hasEnoughGas() {
        return iexecHubService.hasEnoughGas();
    }
}
