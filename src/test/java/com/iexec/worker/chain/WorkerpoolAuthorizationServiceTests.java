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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WorkerpoolAuthorizationServiceTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";

    @InjectMocks
    private WorkerpoolAuthorizationService workerpoolAuthorizationService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     *  isWorkerpoolAuthorizationValid()
     *
     */

    @Test
    void shouldWorkerpoolAuthorizationBeValid() {
        // PRIVATE_KEY_STRING: "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
        // PUBLIC_KEY_STRING: "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";
        String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        Signature signature = new Signature(
                BytesUtils.stringToBytes("0x99f6b19da6aeb2133763a11204b9895c5b7d0478d08ae3d889a6bd6c820b612f"),
                BytesUtils.stringToBytes("0x0b64b1f9ceb8472f4944da55d3b75947a04618bae5ddd57a7a2a2d14c3802b7e"),
                new byte[]{(byte) 27});

        WorkerpoolAuthorization workerpoolAuthorization = WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveWallet)
                .signature(signature)
                .build();

        assertTrue(workerpoolAuthorizationService.isWorkerpoolAuthorizationValid(workerpoolAuthorization, signingAddress));
    }

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
}