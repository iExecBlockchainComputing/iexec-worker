/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.chain;

import com.iexec.common.security.Signature;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.EthAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import static com.iexec.common.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;


@Slf4j
@Service
public class EnclaveAuthorizationService {

    public static boolean isSignature(String hexString) {
        return !StringUtils.isEmpty(hexString) &&
                BytesUtils.stringToBytes(hexString).length == 65; // 32 + 32 + 1
    }

    public static boolean isByte32(String hexString) {
        return !StringUtils.isEmpty(hexString) &&
                BytesUtils.stringToBytes(hexString).length == 32;
    }

    public boolean isVerifiedEnclaveSignature(String chainTaskId,
                                              String resultHash,
                                              String resultSeal,
                                              String enclaveSignature,
                                              String enclaveChallenge) {
        if (StringUtils.isEmpty(resultHash)
                || !isByte32(resultHash)) {
            logError("resultHash", chainTaskId,
                    resultHash, resultSeal, enclaveSignature, enclaveChallenge);
            return false;
        }
        if (StringUtils.isEmpty(resultSeal)
                || !isByte32(resultSeal)) {
            logError("resultSeal", chainTaskId,
                    resultHash, resultSeal, enclaveSignature, enclaveChallenge);
            return false;
        }
        if (StringUtils.isEmpty(enclaveSignature)
                || !isSignature(enclaveSignature)) {
            logError("enclaveSignature", chainTaskId,
                    resultHash, resultSeal, enclaveSignature, enclaveChallenge);
            return false;
        }
        if (StringUtils.isEmpty(enclaveChallenge)
                || !EthAddress.validate(resultHash)) {
            logError("enclaveChallenge", chainTaskId,
                    resultHash, resultSeal, enclaveSignature, enclaveChallenge);
            return false;
        }

        String messageHash =
                TeeEnclaveChallengeSignature.getMessageHash(resultHash,
                        resultSeal);

        return isExpectedSignerOnSignedMessageHash(messageHash,
                new Signature(enclaveSignature), enclaveChallenge);
    }

    private void logError(String errorParam,
                          String chainTaskId,
                          String resultHash,
                          String resultSeal,
                          String enclaveSignature,
                          String enclaveChallenge) {
        log.error("Cannot verify enclave signature [chainTaskId:{}, " +
                        "errorParam:{}, resultHash:{}, resultSeal:{}, " +
                        "enclaveSignature:{}, enclaveChallenge:{}]",
                chainTaskId, errorParam, resultHash, resultSeal,
                enclaveSignature, enclaveChallenge);
    }
}
