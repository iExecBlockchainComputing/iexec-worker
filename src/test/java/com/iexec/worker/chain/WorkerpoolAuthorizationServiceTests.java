/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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
import com.iexec.worker.config.SchedulerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerpoolAuthorizationServiceTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";

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
        WorkerpoolAuthorization workerpoolAuthorization = getWorkerpoolAuthorization();
        workerpoolAuthorization.setChainTaskId(null);
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

    private WorkerpoolAuthorization getWorkerpoolAuthorization() {
        // PRIVATE_KEY_STRING: "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
        // PUBLIC_KEY_STRING: "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";

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
