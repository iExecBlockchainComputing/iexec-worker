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

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.worker.config.SchedulerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerpoolAuthorizationServiceTests {
    private static final String CHAIN_TASK_ID = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

    @Mock
    private SchedulerConfiguration schedulerConfiguration;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private WorkerpoolAuthorizationService workerpoolAuthorizationService;

    // region isWorkerpoolAuthorizationValid()
    @Test
    void shouldWorkerpoolAuthorizationBeValid() {
        final String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";
        assertTrue(workerpoolAuthorizationService.isWorkerpoolAuthorizationValid(getWorkerpoolAuthorization(), signingAddress));
    }
    // endregion

    // region putWorkerpoolAuthorization()
    @Test
    void shouldPutWorkerpoolAuthorization() {
        final String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";
        when(iexecHubService.getOwner(any())).thenReturn(signingAddress);
        final WorkerpoolAuthorization workerpoolAuthorization = getWorkerpoolAuthorization();
        final WorkerpoolAuthorizationService wpAuthorizationService = new WorkerpoolAuthorizationService(schedulerConfiguration, iexecHubService);
        assertTrue(wpAuthorizationService.putWorkerpoolAuthorization(workerpoolAuthorization));
        assertNotNull(wpAuthorizationService.getWorkerpoolAuthorization(workerpoolAuthorization.getChainTaskId()));
    }

    @Test
    void shouldFailToPutWorkerpoolAuthorizationWhenAuthorizationIsInvalid() {
        when(iexecHubService.getOwner(any())).thenReturn("0x000a9c787a972f70f0903890e266f41c795c4dca");
        assertFalse(workerpoolAuthorizationService.putWorkerpoolAuthorization(getWorkerpoolAuthorization()));
    }

    @Test
    void shouldFailToPutWorkerpoolAuthorizationWhenCantGetWorkerPoolOwner() {
        when(iexecHubService.getOwner(any())).thenReturn("");
        assertFalse(workerpoolAuthorizationService.putWorkerpoolAuthorization(getWorkerpoolAuthorization()));
    }

    @Test
    void shouldFailToPutWorkerpoolAuthorizationIfChainTaskIdIsNullInWorkerpoolAuthorization() {
        final WorkerpoolAuthorization workerpoolAuthorization = WorkerpoolAuthorization.builder().chainTaskId(null).build();
        assertFalse(workerpoolAuthorizationService.putWorkerpoolAuthorization(workerpoolAuthorization));
    }

    @Test
    void shouldFailToPutWorkerpoolAuthorizationIfWorkerpoolAuthorizationIsNull() {
        assertFalse(workerpoolAuthorizationService.putWorkerpoolAuthorization(null));
    }

    // endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations = new HashMap<>();
        workerpoolAuthorizations.put(CHAIN_TASK_ID, mock(WorkerpoolAuthorization.class));
        ReflectionTestUtils.setField(workerpoolAuthorizationService, "workerpoolAuthorizations", workerpoolAuthorizations);

        assertTrue(workerpoolAuthorizationService.purgeTask(CHAIN_TASK_ID));
    }

    @Test
    void shouldPurgeTaskEvenThoughEmptyMap() {
        assertTrue(workerpoolAuthorizationService.purgeTask(CHAIN_TASK_ID));
    }

    @Test
    void shouldPurgeTaskEvenThoughNoMatchingTaskId() {
        final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations = new HashMap<>();
        workerpoolAuthorizations.put(CHAIN_TASK_ID + "-wrong", mock(WorkerpoolAuthorization.class));
        ReflectionTestUtils.setField(workerpoolAuthorizationService, "workerpoolAuthorizations", workerpoolAuthorizations);

        assertTrue(workerpoolAuthorizationService.purgeTask(CHAIN_TASK_ID));
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksData() {
        assertDoesNotThrow(workerpoolAuthorizationService::purgeAllTasksData);
    }
    // endregion

    // region getChallenge
    @Test
    void shouldGetChallenge() {
        final WorkerpoolAuthorization workerpoolAuthorization = getWorkerpoolAuthorization();

        final String expectedChallenge = HashUtils.concatenateAndHash(
                workerpoolAuthorization.getChainTaskId(),
                workerpoolAuthorization.getWorkerWallet()
        );

        final String challenge = ReflectionTestUtils.invokeMethod(
                workerpoolAuthorizationService,
                "getChallenge",
                workerpoolAuthorization);

        assertEquals(expectedChallenge, challenge);
    }
    // endregion

    // region isSignedWithEnclaveChallenge
    @Test
    void shouldConfirmSignatureIsSignedWithEnclaveChallenge() throws Exception {
        final ECKeyPair workerKeyPair = Keys.createEcKeyPair();
        final Credentials workerCredentials = Credentials.create(workerKeyPair);
        final String workerWallet = workerCredentials.getAddress();

        final ECKeyPair enclaveKeyPair = Keys.createEcKeyPair();
        final Credentials enclaveCredentials = Credentials.create(enclaveKeyPair);
        final String enclaveWallet = enclaveCredentials.getAddress();

        final WorkerpoolAuthorization workerpoolAuthorization = WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveWallet)
                .build();

        final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations = new HashMap<>();
        workerpoolAuthorizations.put(CHAIN_TASK_ID, workerpoolAuthorization);
        ReflectionTestUtils.setField(workerpoolAuthorizationService, "workerpoolAuthorizations", workerpoolAuthorizations);

        final String challenge = HashUtils.concatenateAndHash(CHAIN_TASK_ID, workerWallet);
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(challenge, enclaveKeyPair);
        final boolean isValid = workerpoolAuthorizationService.isSignedWithEnclaveChallenge(
                CHAIN_TASK_ID,
                signature.getValue());

        assertTrue(isValid);
    }

    @Test
    void shouldRejectSignatureNotSignedWithEnclaveChallenge() {
        final WorkerpoolAuthorization workerpoolAuthorization = getWorkerpoolAuthorization();
        final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations = new HashMap<>();
        workerpoolAuthorizations.put(workerpoolAuthorization.getChainTaskId(), workerpoolAuthorization);
        ReflectionTestUtils.setField(workerpoolAuthorizationService, "workerpoolAuthorizations", workerpoolAuthorizations);

        final Signature invalidSignature = SignatureUtils.signMessageHashAndGetSignature(
                Arrays.toString(BytesUtils.stringToBytes("wrong-challenge")),
                workerpoolAuthorization.getWorkerWallet()
        );

        assertFalse(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(
                workerpoolAuthorization.getChainTaskId(),
                invalidSignature.toString()));
    }

    @Test
    void shouldThrowExceptionWhenWorkerpoolAuthorizationNotFound() {
        assertThrows(NoSuchElementException.class, () ->
                workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, "anySignature")
        );
    }
    // endregion

    private WorkerpoolAuthorization getWorkerpoolAuthorization() {
        final String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        final String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        final String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        final Signature signature = new Signature(
                BytesUtils.stringToBytes("0x99f6b19da6aeb2133763a11204b9895c5b7d0478d08ae3d889a6bd6c820b612f"),
                BytesUtils.stringToBytes("0x0b64b1f9ceb8472f4944da55d3b75947a04618bae5ddd57a7a2a2d14c3802b7e"),
                new byte[]{(byte) 27});

        return WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveWallet)
                .signature(signature)
                .build();
    }
}
