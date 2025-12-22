/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee.tdx;

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeeTdxServiceTests {
    private static final String CHAIN_TASK_ID = "0xe7610523051210870457895cd72959aeef5872a8d328db7557ff3489834c3ce6";
    private static final String TDX_SESSION_ID = "01234567890000" + CHAIN_TASK_ID;
    private static final String TDX_SESSION_VERSION = "0.1.0";
    private static final String DOCKER_ENV_VAR_NAME = "ENV";
    private static final String DOCKER_ENV_VAR_VALUE = "VALUE";

    @Mock
    private SmsService smsService;
    @Mock
    private TeeServicesPropertiesService teeServicesPropertiesService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @InjectMocks
    private TeeTdxService teeTdxService;

    @TempDir
    Path taskBaseDir;

    final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build();

    @Test
    void shouldBeTeeEnabled() {
        assertThat(teeTdxService.isTeeEnabled()).isTrue();
    }

    // region createTeeSession
    @Test
    void shouldCreateTeeSession() throws TeeSessionGenerationException {
        ReflectionTestUtils.setField(teeTdxService, "secretProviderAgent", this.getClass().getClassLoader().getResource("secret_provider_agent").getFile());
        final WorkerpoolAuthorization authorization = WorkerpoolAuthorization.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(smsService.createTeeSession(authorization))
                .thenReturn(new TeeSessionGenerationResponse("sessionId", "secretProvisioningUrl"));
        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID)).thenReturn(taskBaseDir.toString());
        assertThatNoException().isThrownBy(() -> teeTdxService.createTeeSession(authorization));
    }

    @Test
    void shouldFailToCreateTeeSession() throws TeeSessionGenerationException {
        ReflectionTestUtils.setField(teeTdxService, "secretProviderAgent", "/tmp/not-found/secret_provider_agent");
        final WorkerpoolAuthorization authorization = WorkerpoolAuthorization.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(smsService.createTeeSession(authorization))
                .thenReturn(new TeeSessionGenerationResponse("sessionId", "secretProvisioningUrl"));
        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID)).thenReturn(taskBaseDir.toString());
        assertThatThrownBy(() -> teeTdxService.createTeeSession(authorization))
                .isInstanceOf(TeeSessionGenerationException.class)
                .hasFieldOrPropertyWithValue("TeeSessionGenerationError", TeeSessionGenerationError.SECURE_SESSION_STORAGE_CALL_FAILED);
    }
    // endregion

    @Test
    void shouldPrepareTeeForTask() {
        assertThat(teeTdxService.prepareTeeForTask(CHAIN_TASK_ID)).isTrue();
    }

    // region buildPreComputeDockerEnv
    @Test
    void shouldBuildEmptyPreComputeDockerEnvWhenNoSession() {
        assertThat(teeTdxService.buildPreComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildEmptyPreComputeDockerEnvWhenEmptyMap() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "pre-compute", "image_name", "fingerprint", Map.of()));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildPreComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildPreComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "pre-compute", "image_name", "fingerprint", Map.of(DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE)));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildPreComputeDockerEnv(taskDescription))
                .containsExactly(String.format("%s=%s", DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE));
    }
    // endregion

    // region buildComputeDockerEnv
    @Test
    void shouldBuildEmptyComputeDockerEnvWhenNoSession() {
        assertThat(teeTdxService.buildComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildEmptyComputeDockerEnvWhenEmptyMap() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "app", "image_name", "fingerprint", Map.of()));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "app", "image_name", "fingerprint", Map.of(DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE)));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildComputeDockerEnv(taskDescription))
                .containsExactly(String.format("%s=%s", DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE));
    }
    // endregion

    // region buildPostComputeDockerEnv
    @Test
    void shouldBuildEmptyPostComputeDockerEnvWhenNoSession() {
        assertThat(teeTdxService.buildPostComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildEmptyPostComputeDockerEnvWhenEmptyMap() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "post-compute", "image_name", "fingerprint", Map.of()));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildPostComputeDockerEnv(taskDescription)).isEmpty();
    }

    @Test
    void shouldBuildPostComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(new TdxSession.Service(
                "post-compute", "image_name", "fingerprint", Map.of(DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE)));
        final Map<String, TdxSession> sessions = Map.of(CHAIN_TASK_ID, new TdxSession(TDX_SESSION_ID, TDX_SESSION_VERSION, sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", sessions);
        assertThat(teeTdxService.buildPostComputeDockerEnv(taskDescription))
                .containsExactly(String.format("%s=%s", DOCKER_ENV_VAR_NAME, DOCKER_ENV_VAR_VALUE));
    }
    // endregion

    @Test
    void shouldNotRequireAdditionalBindings() {
        assertThat(teeTdxService.getAdditionalBindings()).isEmpty();
    }

    @Test
    void shouldNotRequireDevices() {
        assertThat(teeTdxService.getDevices()).isEmpty();
    }

    // region TEE sessions cache
    private void prefillTeeSessionsCache(final Map<String, TeeSessionGenerationResponse> teeSessions,
                                         final Map<String, TdxSession> tdxSessions) {
        teeSessions.put("taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"));
        teeSessions.put("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2"));
        ReflectionTestUtils.setField(teeTdxService, "teeSessions", teeSessions);
        tdxSessions.put("taskId1", new TdxSession("sessionId1", TDX_SESSION_VERSION, List.of()));
        tdxSessions.put("taskId2", new TdxSession("sessionId2", TDX_SESSION_VERSION, List.of()));
        ReflectionTestUtils.setField(teeTdxService, "tdxSessions", tdxSessions);
    }

    @Test
    void shouldNotModifyCacheWhenNoSessionInCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        final Map<String, TdxSession> tdxSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions, tdxSessions);
        teeTdxService.purgeTask("taskId3");
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of(
                        "taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"),
                        "taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
        assertThat(tdxSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of(
                        "taskId1", new TdxSession("sessionId1", TDX_SESSION_VERSION, List.of()),
                        "taskId2", new TdxSession("sessionId2", TDX_SESSION_VERSION, List.of())));
    }

    @Test
    void shouldRemoveTeeSessionFromCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        final Map<String, TdxSession> tdxSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions, tdxSessions);
        teeTdxService.purgeTask("taskId1");
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
        assertThat(tdxSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of("taskId2", new TdxSession("sessionId2", TDX_SESSION_VERSION, List.of())));
    }

    @Test
    void shouldRemoveAllTeeSessionsFromCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        final Map<String, TdxSession> tdxSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions, tdxSessions);
        teeTdxService.purgeAllTasksData();
        assertThat(teeSessions).isEmpty();
        assertThat(tdxSessions).isEmpty();
    }
    // end region
}
