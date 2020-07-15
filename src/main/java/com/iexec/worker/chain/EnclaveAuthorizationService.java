package com.iexec.worker.chain;

import com.iexec.common.security.Signature;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.iexec.common.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;


@Slf4j
@Service
public class EnclaveAuthorizationService {

    public boolean isVerifiedEnclaveSignature(String chainTaskId, String resultHash, String resultSeal,
                                              String enclaveSignature, String enclaveChallenge) {
        if (enclaveChallenge == null || enclaveChallenge.isEmpty()) {
            log.error("Cant verify enclave signature (enclave challenge not found) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (enclaveSignature == null || enclaveSignature.isEmpty()) {
            log.error("Cant verify enclave signature (enclave signature not found) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);

        return isExpectedSignerOnSignedMessageHash(messageHash,
                new Signature(enclaveSignature), enclaveChallenge);
    }
}
