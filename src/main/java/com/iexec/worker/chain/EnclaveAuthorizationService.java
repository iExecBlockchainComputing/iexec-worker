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
