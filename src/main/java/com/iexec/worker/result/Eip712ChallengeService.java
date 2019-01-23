package com.iexec.worker.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.security.SignatureService;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.common.security.Signature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        String r = BytesUtils.bytesToString(signature.getSignR());
        String s = BytesUtils.bytesToString(signature.getSignS());
        String v = BytesUtils.bytesToString(new byte[] {signature.getSignV()});

        String signatureString = String.join("", "Ox", r, s, v);

        String authoriaztionToken = String.join("_", challengeString, signatureString, signature.getWorkerWallet());

        return authoriaztionToken;

    }

}
