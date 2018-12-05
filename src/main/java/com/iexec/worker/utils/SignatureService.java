package com.iexec.worker.utils;

import com.iexec.common.security.Authorization;
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

    public Authorization createAuthorization(String workerWallet) {
        byte[] message = Hash.sha3(BytesUtils.stringToBytes(workerWallet));

        ECKeyPair keyPair = credentialsService.getCredentials().getEcKeyPair();
        Sign.SignatureData sign = Sign.signMessage(message, keyPair, false);

        return Authorization.builder()
                .workerWallet(workerWallet)
                .signR(sign.getR())
                .signS(sign.getS())
                .signV(sign.getV())
                .build();
    }
}
