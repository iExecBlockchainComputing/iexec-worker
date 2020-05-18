package com.iexec.worker.chain;

import com.iexec.common.security.Signature;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
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
        MockitoAnnotations.initMocks(this);
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
}