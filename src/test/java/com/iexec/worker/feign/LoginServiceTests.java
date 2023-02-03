/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.security.Signature;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import static com.iexec.worker.feign.LoginService.TOKEN_PREFIX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "MOVED_PERMANENTLY", "BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "INTERNAL_SERVER_ERROR"})
    void shouldNotLoginOnBadChallengeStatusCode(HttpStatus status) {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(ResponseEntity.status(status).build());
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
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(ResponseEntity.ok(challenge));
        assertAll(
                () -> assertEquals("", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress())
        );
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "MOVED_PERMANENTLY", "BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "INTERNAL_SERVER_ERROR"})
    void shouldNotLoginOnBadLoginStatusCode(HttpStatus status) {
        Credentials credentials = generateCredentials();
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(ResponseEntity.ok("challenge"));
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn(ResponseEntity.status(status).build());
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
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(ResponseEntity.ok("challenge"));
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn(ResponseEntity.ok(token));
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
        when(coreClient.getChallenge(credentials.getAddress())).thenReturn(ResponseEntity.ok("challenge"));
        Signature signature = SignatureUtils.hashAndSign("challenge", credentials.getAddress(), credentials.getEcKeyPair());
        when(coreClient.login(credentials.getAddress(), signature)).thenReturn(ResponseEntity.ok("token"));
        assertAll(
                () -> assertEquals(TOKEN_PREFIX + "token", loginService.login()),
                () -> verify(coreClient).getChallenge(credentials.getAddress()),
                () -> verify(coreClient).login(credentials.getAddress(), signature)
        );
    }

}
