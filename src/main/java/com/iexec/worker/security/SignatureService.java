package com.iexec.worker.security;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.CredentialsService;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

@Service
public class SignatureService {

    private CredentialsService credentialsService;

    public SignatureService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public Signature createSignature(String stringToSign) {
        byte[] message = Hash.sha3(BytesUtils.stringToBytes(stringToSign));

        ECKeyPair keyPair = credentialsService.getCredentials().getEcKeyPair();
        Sign.SignatureData sign = Sign.signMessage(message, keyPair, false);

        return Signature.builder()
                .workerWallet(credentialsService.getCredentials().getAddress())
                .signR(sign.getR())
                .signS(sign.getS())
                .signV(sign.getV())
                .build();
    }
}
