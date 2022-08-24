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

package com.iexec.worker.tee.scone;

import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeWorkflowConfiguration;
import com.iexec.worker.tee.TeeWorkflowConfigurationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeeSconeServiceTests {

    private static final String REGISTRY_NAME = "registryName";
    private static final String IMAGE_URI = REGISTRY_NAME +"/some/image/name:x.y";
    private static final String SESSION_ID = "sessionId";
    private static final String CAS_URL = "casUrl";
    private static final String LAS_URL = "lasUrl";
    private final static TeeSessionGenerationResponse SESSION = new TeeSessionGenerationResponse(SESSION_ID, CAS_URL);
    private static final boolean SHOW_VERSION = true;
    private static final String LOG_LEVEL = "debug";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final TaskDescription TASK_DESCRIPTION = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .appEnclaveConfiguration(TeeEnclaveConfiguration.builder().heapSize(1024).build())
            .build();
    public static final String REGISTRY_USERNAME = "registryUsername";
    public static final String REGISTRY_PASSWORD = "registryPassword";
    public static final long HEAP_SIZE = 1024L;

    @InjectMocks
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
    @Mock
    private SgxService sgxService;
    @Mock
    private TeeWorkflowConfigurationService teeWorkflowConfigurationService;
    @Mock
    private LasServicesManager lasServicesManager;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(sconeConfig.getRegistryName()).thenReturn(REGISTRY_NAME);
        when(sconeConfig.getRegistryUsername()).thenReturn(REGISTRY_USERNAME);
        when(sconeConfig.getRegistryPassword()).thenReturn(REGISTRY_PASSWORD);

        final TeeWorkflowConfiguration teeWorkflowConfig = TeeWorkflowConfiguration.builder()
                .preComputeHeapSize(HEAP_SIZE)
                .postComputeHeapSize(HEAP_SIZE)
                .build();
        when(teeWorkflowConfigurationService.getOrCreateTeeWorkflowConfiguration(CHAIN_TASK_ID))
                .thenReturn(teeWorkflowConfig);

        final LasService lasService = mock(LasService.class);
        when(lasService.getUrl()).thenReturn(LAS_URL);
        when(lasService.getSconeConfig()).thenReturn(sconeConfig);
        when(lasServicesManager.getLas(CHAIN_TASK_ID)).thenReturn(lasService);
    }

    @Test
    void shouldBuildPreComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        Assertions.assertThat(teeSconeService.buildPreComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/pre-compute",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

    @Test
    void shouldBuildComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        Assertions.assertThat(teeSconeService.buildComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/app",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

    @Test
    void shouldBuildPostComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        Assertions.assertThat(teeSconeService.buildPostComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/post-compute",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

}