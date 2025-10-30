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

package com.iexec.worker.tee.gramine;

import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TeeGramineServiceTests {
    private static final String SESSION_ID = "0x123_session_id";
    private static final String SPS_URL = "http://spsUrl";
    private static final TeeSessionGenerationResponse TEE_SESSION_GENERATION_RESPONSE = new TeeSessionGenerationResponse(
            SESSION_ID,
            SPS_URL
    );

    @Mock
    SgxService sgxService;
    @Mock
    SmsClientProvider smsClientProvider;
    @Mock
    TeeServicesPropertiesService teeServicesPropertiesService;

    @InjectMocks
    TeeGramineService teeGramineService;

    // region prepareTeeForTask
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldPrepareTeeForTask(String chainTaskId) {
        assertTrue(teeGramineService.prepareTeeForTask(chainTaskId));

        verifyNoInteractions(sgxService, smsClientProvider, teeServicesPropertiesService);
    }
    // endregion

    // region buildPreComputeDockerEnv
    @ParameterizedTest
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildPreComputeDockerEnv(String chainTaskId) {
        ReflectionTestUtils.setField(teeGramineService, "teeSessions", Map.of(chainTaskId, TEE_SESSION_GENERATION_RESPONSE));
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildPreComputeDockerEnv(taskDescription);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region buildComputeDockerEnv
    @ParameterizedTest
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildComputeDockerEnv(String chainTaskId) {
        ReflectionTestUtils.setField(teeGramineService, "teeSessions", Map.of(chainTaskId, TEE_SESSION_GENERATION_RESPONSE));
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildComputeDockerEnv(taskDescription);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region buildPostComputeDockerEnv
    @ParameterizedTest
    @ValueSource(strings = {"", "0x123", "chainTaskId"})
    void shouldBuildPostComputeDockerEnv(String chainTaskId) {
        ReflectionTestUtils.setField(teeGramineService, "teeSessions", Map.of(chainTaskId, TEE_SESSION_GENERATION_RESPONSE));
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(chainTaskId).build();
        final List<String> env = teeGramineService.buildPostComputeDockerEnv(taskDescription);

        assertEquals(2, env.size());
        assertTrue(env.containsAll(List.of(
                "sps=http://spsUrl",
                "session=0x123_session_id"
        )));
    }
    // endregion

    // region getAdditionalBindings
    @Test
    void shouldGetAdditionalBindings() {
        final Collection<String> bindings = teeGramineService.getAdditionalBindings();

        assertEquals(1, bindings.size());
        assertTrue(bindings.contains("/var/run/aesmd/aesm.socket:/var/run/aesmd/aesm.socket"));
    }
    // endregion

    // region TEE sessions cache
    private void prefillTeeSessionsCache(final Map<String, TeeSessionGenerationResponse> teeSessions) {
        teeSessions.put("taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"));
        teeSessions.put("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2"));
        ReflectionTestUtils.setField(teeGramineService, "teeSessions", teeSessions);
    }

    @Test
    void shouldNotModifyCacheWhenNoSessionInCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions);
        teeGramineService.purgeTask("taskId3");
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of(
                        "taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"),
                        "taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
    }

    @Test
    void shouldRemoveTeeSessionFromCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions);
        teeGramineService.purgeTask("taskId1");
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
    }

    @Test
    void shouldRemoveAllTeeSessionsFromCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions);
        teeGramineService.purgeAllTasksData();
        assertThat(teeSessions).isEmpty();
    }
    // endregion
}
