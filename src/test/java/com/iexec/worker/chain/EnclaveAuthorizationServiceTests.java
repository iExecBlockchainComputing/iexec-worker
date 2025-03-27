/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class EnclaveAuthorizationServiceTests {

    @InjectMocks
    private EnclaveAuthorizationService enclaveAuthorizationService;

    @Test
    void isVerifiedEnclaveSignature() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        final String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        final String messageHash = HashUtils.concatenateAndHash(resultHash, resultSeal);
        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        final String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        final boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        assertThat(isVerifiedEnclaveSignature).isTrue();
    }

    @Test
    void isNotVerifiedEnclaveSignatureSinceWrongResultHash() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultHash = "0x1";
        final String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        final String messageHash = HashUtils.concatenateAndHash(resultHash, resultSeal);
        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        final String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        final boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        assertThat(isVerifiedEnclaveSignature).isFalse();
    }

    @Test
    void isNotVerifiedEnclaveSignatureSinceWrongResultSeal() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        final String resultSeal = "0x3";

        final String messageHash = HashUtils.concatenateAndHash(resultHash, resultSeal);
        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        final String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        final boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        assertThat(isVerifiedEnclaveSignature).isFalse();
    }

    @Test
    void isNotVerifiedEnclaveSignatureSinceWrongEnclaveChallenge() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        final String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        final String messageHash = HashUtils.concatenateAndHash(resultHash, resultSeal);
        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        final String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        final boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), "0x1");
        assertThat(isVerifiedEnclaveSignature).isFalse();
    }

    @Test
    void isNotVerifiedEnclaveSignatureSinceWrongEnclaveSignature() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        final String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        final boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, "0x1", credentials.getAddress());
        assertThat(isVerifiedEnclaveSignature).isFalse();
    }
}
