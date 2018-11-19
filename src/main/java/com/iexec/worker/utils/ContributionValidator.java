package com.iexec.worker.utils;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import org.bouncycastle.util.Arrays;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;

public class ContributionValidator {

    // Verify that the contributionAuthorization is valid and coming from signerAddress
    public static boolean isValid(ContributionAuthorization auth, String signerAddress){
        // create the hash that was used in the signature in the core
        byte[] hash = Hash.sha3(Arrays.concatenate(
                BytesUtils.stringToBytes(auth.getWorkerWallet()),
                BytesUtils.stringToBytes(auth.getChainTaskId()),
                BytesUtils.stringToBytes(auth.getEnclave())));
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
