package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

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

        Optional<ChainTask> optionalChain = iexecHubService.getChainTask(chainTaskId);
        if (!optionalChain.isPresent()) {
            return false;
        }
        ChainTask chainTask = optionalChain.get();

        boolean isTaskActive = chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);
        boolean consensusDeadlineReached = chainTask.getConsensusDeadline() < new Date().getTime();

        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTaskId);
        if (!optionalContribution.isPresent()) {
            return false;
        }
        ChainContribution chainContribution = optionalContribution.get();
        boolean isContributionUnset = chainContribution.getStatus().equals(ChainContributionStatus.UNSET);

        return isTaskActive && !consensusDeadlineReached && isContributionUnset;
    }

    public boolean contribute(ContributionAuthorization contribAuth, String deterministHash) throws Exception {

        String seal = computeSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash);
        log.debug("Computation of the seal [wallet:{}, chainTaskId:{}, deterministHash:{}, seal:{}]",
                contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), deterministHash, seal);

        // For now no SGX used!
        String contributionValue = HashUtils.concatenateAndHash(contribAuth.getChainTaskId(), deterministHash);
        TransactionReceipt receipt = iexecHubService.contribute(contribAuth, contributionValue, seal);

        return receipt != null && receipt.isStatusOK();
    }

    private String computeSeal(String walletAddress, String chainTaskId, String deterministHash) {
        return HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);
    }

    public boolean isContributionAuthorizationValid(ContributionAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] hash = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclave()));
        byte[] hashTocheck = SignatureUtils.getEthereumMessageHash(hash);

        // check that the public address of the signer can be found
        for (int i = 0; i < 4; i++) {
            BigInteger publicKey = Sign.recoverFromSignature((byte) i,
                    new ECDSASignature(new BigInteger(1, auth.getSignR()), new BigInteger(1, auth.getSignS())), hashTocheck);

            if (publicKey != null) {
                String addressRecovered = "0x" + Keys.getAddress(publicKey);

                if (addressRecovered.equals(signerAddress)) {
                    return true;
                }
            }
        }

        return false;
    }
}
