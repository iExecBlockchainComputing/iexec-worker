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
import com.iexec.common.utils.SignatureUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class EnclaveAuthorizationServiceTests {

    @InjectMocks
    private EnclaveAuthorizationService enclaveAuthorizationService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void isVerifiedEnclaveSignature() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);
        Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        Assert.assertTrue(isVerifiedEnclaveSignature);
    }

    @Test
    public void isNotVerifiedEnclaveSignatureSinceWrongResultHash() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultHash = "0x1";
        String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);
        Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        Assert.assertFalse(isVerifiedEnclaveSignature);
    }

    @Test
    public void isNotVerifiedEnclaveSignatureSinceNoResultSeal() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultSeal = "0x3";

        String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);
        Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), credentials.getAddress());
        Assert.assertFalse(isVerifiedEnclaveSignature);
    }

    @Test
    public void isNotVerifiedEnclaveSignatureSinceWrongEnclaveChallenge() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        String messageHash = TeeEnclaveChallengeSignature.getMessageHash(resultHash, resultSeal);
        Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        String hexPrivateKey = Numeric.toHexStringWithPrefix(credentials.getEcKeyPair().getPrivateKey());
        Signature signature = SignatureUtils.signMessageHashAndGetSignature(messageHash, hexPrivateKey);

        boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, signature.getValue(), "0x1");
        Assert.assertFalse(isVerifiedEnclaveSignature);
    }

    @Test
    public void isNotVerifiedEnclaveSignatureSinceWrongEnclaveSignature() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultHash = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultSeal = "0x0000000000000000000000000000000000000000000000000000000000000003";

        Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        boolean isVerifiedEnclaveSignature = enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId, resultHash, resultSeal, "0x1", credentials.getAddress());
        Assert.assertFalse(isVerifiedEnclaveSignature);
    }
}