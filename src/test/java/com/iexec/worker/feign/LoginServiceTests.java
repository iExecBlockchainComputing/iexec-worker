/*
 * Copyright 2022-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.feign;

import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.iexec.worker.feign.LoginService.TOKEN_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LoginServiceTests {

    @Mock
    CoreClient coreClient;
    @Mock
    CredentialsService credentialsService;
    @InjectMocks
    private LoginService loginService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @SneakyThrows
    private Credentials generateCredentials() {
        ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        return Credentials.create(ecKeyPair);
    }

    @Test
    void shouldNotLoginOnBadChallengeStatusCode() {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenThrow(FeignException.class);
        assertAll(
                () -> assertEquals("", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "")
    void shouldNotLoginOnEmptyChallenge(String challenge) {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(challenge);
        assertAll(
                () -> assertEquals("", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress())
        );
    }

    @Test
    void shouldNotLoginOnBadLoginStatusCode() {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn("challenge");
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenThrow(FeignException.class);
        assertAll(
                () -> assertEquals("", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress()),
                () -> verify(coreClient).login(credentials.getAddress(), signature)
        );
    }

    @NullSource
    @ValueSource(strings = "")
    @ParameterizedTest
    void shouldNotLoginOnEmptyToken(String token) {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn("challenge");
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn(token);
        assertAll(
                () -> assertEquals("", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress()),
                () -> verify(coreClient).login(credentials.getAddress(), signature)
        );
    }

    @Test
    void shouldLogin() {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn("challenge");
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn("token");
        assertAll(
                () -> assertEquals(TOKEN_PREFIX + "token", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress()),
                () -> verify(coreClient).login(credentials.getAddress(), signature)
        );
    }

    @Test
    void shouldLoginOnceOnSimultaneousCalls(CapturedOutput output) throws InterruptedException, ExecutionException, TimeoutException {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn("challenge");
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn("token");
        CompletableFuture<Void> run1 = CompletableFuture.runAsync(() -> loginService.login());
        CompletableFuture<Void> run2 = CompletableFuture.runAsync(() -> loginService.login());
        CompletableFuture<Void> run3 = CompletableFuture.runAsync(() -> loginService.login());
        CompletableFuture.allOf(run1, run2, run3).get(1L, TimeUnit.SECONDS);
        assertThat(output.getOut())
                .contains("login already ongoing", "login already ongoing", "Retrieved new JWT token from scheduler");
        assertAll(
                () -> verify(coreClient).getChallenge(credentials.getAddress()),
                () -> verify(coreClient).login(credentials.getAddress(), signature)
        );
    }

}
