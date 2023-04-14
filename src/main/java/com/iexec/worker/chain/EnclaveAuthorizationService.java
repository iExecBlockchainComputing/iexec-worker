/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.utils.EthAddress;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.tee.TeeEnclaveChallengeSignature;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.iexec.commons.poco.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;


@Slf4j
@Service
public class EnclaveAuthorizationService {

    public boolean isVerifiedEnclaveSignature(String chainTaskId,
                                              String resultHash,
                                              String resultSeal,
                                              String enclaveSignature,
                                              String enclaveChallenge) {
        String baseErrorMessage =
                "Cannot verify enclave signature with invalid ";
        if (!BytesUtils.isNonZeroedBytes32(resultHash)) {
            log.error(baseErrorMessage + "result hash [chainTaskId:{}, " +
                    "resultHash:{}]", chainTaskId, resultHash);
            return false;
        }
        if (!BytesUtils.isNonZeroedBytes32(resultSeal)) {
            log.error(baseErrorMessage + "result seal [chainTaskId:{}, " +
                    "resultSeal:{}]", chainTaskId, resultSeal);
            return false;
        }
        if (!SignatureUtils.isSignature(enclaveSignature)) {
            log.error(baseErrorMessage + "enclave signature [chainTaskId:{}, " +
                    "enclaveSignature:{}]", chainTaskId, enclaveSignature);
            return false;
        }
        if (!EthAddress.validate(enclaveChallenge)) {
            log.error(baseErrorMessage + "enclave challenge [chainTaskId:{}, " +
                    "enclaveChallenge:{}]", chainTaskId, enclaveChallenge);
            return false;
        }

        String messageHash =
                TeeEnclaveChallengeSignature.getMessageHash(resultHash,
                        resultSeal);

        return isExpectedSignerOnSignedMessageHash(messageHash,
                new Signature(enclaveSignature), enclaveChallenge);
    }

}
