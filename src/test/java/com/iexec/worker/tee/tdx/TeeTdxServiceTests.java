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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeeTdxServiceTests {
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

    @Test
    void shouldBeTeeEnabled() {
        assertThat(teeTdxService.isTeeEnabled()).isTrue();
    }

    @Test
    void shouldCreateTeeSession() throws TeeSessionGenerationException {
        ReflectionTestUtils.setField(teeTdxService, "secretProviderAgent", this.getClass().getClassLoader().getResource("secret_provider_agent").getFile());
        final WorkerpoolAuthorization authorization = WorkerpoolAuthorization.builder().chainTaskId("").build();
        when(smsService.createTeeSession(authorization))
                .thenReturn(new TeeSessionGenerationResponse("sessionId", "secretProvisioningUrl"));
        when(workerConfigurationService.getTaskBaseDir("")).thenReturn(taskBaseDir.toString());
        assertThatNoException().isThrownBy(() -> teeTdxService.createTeeSession(authorization));
    }

    @Test
    void shouldPrepareTeeForTask() {
        assertThat(teeTdxService.prepareTeeForTask("")).isTrue();
    }

    @Test
    void shouldBuildPreComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(
                new TdxSession.Service("pre-compute", "", "", Map.of()));
        final Map<String, TdxSession> sessions = Map.of("", new TdxSession("", "", sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "sessions", sessions);
        assertThat(teeTdxService.buildPreComputeDockerEnv(TaskDescription.builder().chainTaskId("").build())).isEmpty();
    }

    @Test
    void shouldBuildComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(
                new TdxSession.Service("app", "", "", Map.of()));
        final Map<String, TdxSession> sessions = Map.of("", new TdxSession("", "", sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "sessions", sessions);
        assertThat(teeTdxService.buildComputeDockerEnv(TaskDescription.builder().chainTaskId("").build())).isEmpty();
    }

    @Test
    void shouldBuildPostComputeDockerEnv() {
        final List<TdxSession.Service> sessionServices = List.of(
                new TdxSession.Service("post-compute", "", "", Map.of()));
        final Map<String, TdxSession> sessions = Map.of("", new TdxSession("", "", sessionServices));
        ReflectionTestUtils.setField(teeTdxService, "sessions", sessions);
        assertThat(teeTdxService.buildPostComputeDockerEnv(TaskDescription.builder().chainTaskId("").build())).isEmpty();
    }

    @Test
    void shouldNotRequireAdditionalBindings() {
        assertThat(teeTdxService.getAdditionalBindings()).isEmpty();
    }

    @Test
    void shouldNotRequireDevices() {
        assertThat(teeTdxService.getDevices()).isEmpty();
    }
}
