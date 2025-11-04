/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee;

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class TeeServiceTests {
    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final WorkerpoolAuthorization wpAuthorization = WorkerpoolAuthorization.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .build();

    @Mock
    SmsService smsService;

    @InjectMocks
    TeeServiceMock teeService;

    // region TEE sessions cache
    @Test
    void shouldAddTeeSessionGenerationResponseToCache() throws TeeSessionGenerationException {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(teeService, "teeSessions", teeSessions);
        final TeeSessionGenerationResponse teeSession = new TeeSessionGenerationResponse("sessionId", "sessionUrl");
        when(smsService.createTeeSession(wpAuthorization)).thenReturn(teeSession);
        teeService.createTeeSession(wpAuthorization);
        assertThat(teeSessions).containsEntry(CHAIN_TASK_ID, teeSession);
    }

    @Test
    void shouldLogAndErrorAndDoNothingWhenSessionAlreadyPresent(final CapturedOutput output) throws TeeSessionGenerationException {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        teeSessions.put("taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"));
        teeSessions.put("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2"));
        ReflectionTestUtils.setField(teeService, "teeSessions", teeSessions);
        teeService.createTeeSession(WorkerpoolAuthorization.builder().chainTaskId("taskId1").build());
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of("taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"),
                        "taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
        assertThat(output.getAll()).contains("TEE session already exists for task [chainTaskId:taskId1]");
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldThrowExceptionOnSessionRetrievalFailure() throws TeeSessionGenerationException {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(teeService, "teeSessions", teeSessions);
        when(smsService.createTeeSession(wpAuthorization)).thenReturn(null);
        assertThatThrownBy(() -> teeService.createTeeSession(wpAuthorization))
                .isInstanceOf(TeeSessionGenerationException.class)
                .hasMessage(null)
                .hasFieldOrPropertyWithValue("teeSessionGenerationError", TeeSessionGenerationError.UNKNOWN_ISSUE);
        assertThat(teeSessions).isEmpty();
    }
    // endregion
}
