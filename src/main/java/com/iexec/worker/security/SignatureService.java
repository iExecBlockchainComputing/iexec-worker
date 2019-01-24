package com.iexec.worker.security;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.CredentialsService;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@Service
public class SignatureService {

    private CredentialsService credentialsService;

    public SignatureService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public Signature hashAndSign(String stringToSign) {
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

    public String hashAndSignAsString(String stringToSign) {
        byte[] message = Hash.sha3(BytesUtils.stringToBytes(stringToSign));

        ECKeyPair keyPair = credentialsService.getCredentials().getEcKeyPair();
        Sign.SignatureData sign = Sign.signMessage(message, keyPair, false);

        return createStringFromSignature(sign);
    }

    public String signAsString(String stringToSign) {
        byte[] message = Numeric.hexStringToByteArray(stringToSign);

        ECKeyPair keyPair = credentialsService.getCredentials().getEcKeyPair();
        Sign.SignatureData sign = Sign.signMessage(message, keyPair, false);

        return createStringFromSignature(sign);
    }

    private String createStringFromSignature(Sign.SignatureData  sign) {
        String r = Numeric.toHexString(sign.getR());
        String s = Numeric.toHexString(sign.getS());
        String v = Integer.toHexString(sign.getV());

        return String.join("", r, Numeric.cleanHexPrefix(s), v);
    }
}
