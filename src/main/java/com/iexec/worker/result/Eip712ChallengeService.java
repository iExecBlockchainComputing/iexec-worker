package com.iexec.worker.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.security.SignatureService;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.common.security.Signature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.utils.Numeric;

@Service
@Slf4j
public class Eip712ChallengeService {

    private SignatureService signatureService;

    Eip712ChallengeService(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    public String buildAuthorizationToken(Eip712Challenge eip712Challenge) {
        String challengeString = Eip712ChallengeUtils.getEip712ChallengeString(eip712Challenge);

        Signature signature = signatureService.createSignature(challengeString);

        String r = Numeric.toHexString(signature.getSignR());
        String s = Numeric.toHexString(signature.getSignS());
        String v = Integer.toHexString(signature.getSignV());

        String signatureString = String.join("", r, Numeric.cleanHexPrefix(s), v);

        String authoriaztionToken = String.join("_", challengeString, signatureString, signature.getWorkerWallet());

        return authoriaztionToken;
    }
}
