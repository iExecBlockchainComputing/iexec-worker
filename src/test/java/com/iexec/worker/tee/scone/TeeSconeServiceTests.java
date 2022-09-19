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

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeeSconeServiceTests {

    private static final String REGISTRY_NAME = "registryName";
    private static final String SESSION_ID = "sessionId";
    private static final String CAS_URL = "casUrl";
    private static final String LAS_URL = "lasUrl";
    private static final TeeSessionGenerationResponse SESSION = new TeeSessionGenerationResponse(SESSION_ID, CAS_URL);
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
    @Spy
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
    @Mock
    private SgxService sgxService;
    @Mock
    private SmsClientProvider smsClientProvider;
    @Mock
    private IexecHubAbstractService iexecHubService;
    @Mock
    private TeeAppProperties preComputeProperties;
    @Mock
    private TeeAppProperties postComputeProperties;
    @Mock
    private SconeServicesProperties properties;
    @Mock
    private TeeServicesPropertiesService teeServicesPropertiesService;
    @Mock
    private LasServicesManager lasServicesManager;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(sconeConfig.getRegistryName()).thenReturn(REGISTRY_NAME);
        when(sconeConfig.getRegistryUsername()).thenReturn(REGISTRY_USERNAME);
        when(sconeConfig.getRegistryPassword()).thenReturn(REGISTRY_PASSWORD);

        when(preComputeProperties.getHeapSizeInBytes()).thenReturn(HEAP_SIZE);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(HEAP_SIZE);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(properties.getPostComputeProperties()).thenReturn(postComputeProperties);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID))
                .thenReturn(properties);

        final LasService lasService = mock(LasService.class);
        when(lasService.getUrl()).thenReturn(LAS_URL);
        when(lasService.getSconeConfig()).thenReturn(sconeConfig);
        when(lasServicesManager.getLas(CHAIN_TASK_ID)).thenReturn(lasService);
    }

    // region areTeePrerequisitesMetForTask
    @Test
    void shouldTeePrerequisiteMetForTask() {
        doReturn(true).when(teeSconeService).isTeeEnabled();
        doReturn(TASK_DESCRIPTION).when(iexecHubService).getTaskDescription(CHAIN_TASK_ID);
        doReturn(null).when(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        doReturn(null).when(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        doReturn(true).when(teeSconeService).prepareTeeForTask(CHAIN_TASK_ID);

        final Optional<ReplicateStatusCause> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue).isEmpty();

        verify(teeSconeService).isTeeEnabled();
        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(teeSconeService).prepareTeeForTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceTeeNotEnabled() {
        doReturn(false).when(teeSconeService).isTeeEnabled();

        final Optional<ReplicateStatusCause> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .isPresent()
                .contains(TEE_NOT_SUPPORTED);

        verify(teeSconeService, times(1)).isTeeEnabled();
        verify(smsClientProvider, times(0)).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(teeServicesPropertiesService, times(0)).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(teeSconeService, times(0)).prepareTeeForTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceSmsClientCantBeLoaded() {
        doReturn(true).when(teeSconeService).isTeeEnabled();
        doReturn(TASK_DESCRIPTION).when(iexecHubService).getTaskDescription(CHAIN_TASK_ID);
        doThrow(SmsClientCreationException.class).when(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);

        final Optional<ReplicateStatusCause> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .isPresent()
                .contains(UNKNOWN_SMS);

        verify(teeSconeService, times(1)).isTeeEnabled();
        verify(smsClientProvider, times(1)).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(teeServicesPropertiesService, times(0)).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(teeSconeService, times(0)).prepareTeeForTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceTeeWorkflowConfigurationCantBeLoaded() {
        doReturn(true).when(teeSconeService).isTeeEnabled();
        doReturn(TASK_DESCRIPTION).when(iexecHubService).getTaskDescription(CHAIN_TASK_ID);
        doReturn(null).when(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        doThrow(SmsClientCreationException.class).when(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);

        final Optional<ReplicateStatusCause> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .isPresent()
                .contains(GET_TEE_SERVICES_CONFIGURATION_FAILED);

        verify(teeSconeService, times(1)).isTeeEnabled();
        verify(smsClientProvider, times(1)).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(teeServicesPropertiesService, times(1)).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(teeSconeService, times(0)).prepareTeeForTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceCantPrepareTee() {
        doReturn(true).when(teeSconeService).isTeeEnabled();
        doReturn(TASK_DESCRIPTION).when(iexecHubService).getTaskDescription(CHAIN_TASK_ID);
        doReturn(null).when(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        doReturn(null).when(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        doReturn(false).when(teeSconeService).prepareTeeForTask(CHAIN_TASK_ID);

        final Optional<ReplicateStatusCause> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .isPresent()
                .contains(TEE_PREPARATION_FAILED);

        verify(teeSconeService, times(1)).isTeeEnabled();
        verify(smsClientProvider, times(1)).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(teeServicesPropertiesService, times(1)).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(teeSconeService, times(1)).prepareTeeForTask(CHAIN_TASK_ID);
    }
    // endregion

    // region buildPreComputeDockerEnv
    @Test
    void shouldBuildPreComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        assertThat(teeSconeService.buildPreComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/pre-compute",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }
    // endregion

    // region buildComputeDockerEnv
    @Test
    void shouldBuildComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        assertThat(teeSconeService.buildComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/app",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }
    // endregion

    // region buildPostComputeDockerEnv
    @Test
    void shouldBuildPostComputeDockerEnv() {
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        assertThat(teeSconeService.buildPostComputeDockerEnv(TASK_DESCRIPTION, SESSION))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/post-compute",
                    "SCONE_HEAP=" + HEAP_SIZE,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }
    // endregion
}