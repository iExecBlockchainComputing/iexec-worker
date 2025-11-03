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

package com.iexec.worker.tee.scone;

import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.TeeServicesPropertiesCreationException;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import com.iexec.worker.workflow.WorkflowError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeeSconeServiceTests {

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
    public static final long HEAP_SIZE = 1024L;

    @InjectMocks
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
    @Mock
    private SgxService sgxService;
    @Mock
    private SmsService smsService;
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
    @Mock
    private SmsClient smsClient;

    // region isTeeEnabled
    @Test
    void shouldTeeBeEnabled() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        assertThat(teeSconeService.isTeeEnabled()).isTrue();
    }

    @Test
    void shouldTeeNotBeEnabled() {
        when(sgxService.isSgxEnabled()).thenReturn(false);
        assertThat(teeSconeService.isTeeEnabled()).isFalse();
    }
    // endregion

    // region areTeePrerequisitesMetForTask
    @Test
    void shouldTeePrerequisiteMetForTask() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(null);
        when(lasServicesManager.startLasService(CHAIN_TASK_ID)).thenReturn(true);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue).isEmpty();

        verify(sgxService, times(2)).isSgxEnabled();
        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(lasServicesManager).startLasService(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceTeeNotEnabled() {
        when(sgxService.isSgxEnabled()).thenReturn(false);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .containsExactly(new WorkflowError(TEE_NOT_SUPPORTED));

        verify(sgxService, times(2)).isSgxEnabled();
        verifyNoInteractions(smsService, teeServicesPropertiesService, lasServicesManager);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceSmsClientCantBeLoaded() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenThrow(SmsClientCreationException.class);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .containsExactly(new WorkflowError(UNKNOWN_SMS));

        verify(sgxService, times(2)).isSgxEnabled();
        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verifyNoInteractions(teeServicesPropertiesService, lasServicesManager);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceTeeWorkflowConfigurationCantBeLoaded() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID))
                .thenThrow(TeeServicesPropertiesCreationException.class);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .containsExactly(new WorkflowError(GET_TEE_SERVICES_CONFIGURATION_FAILED));

        verify(sgxService, times(2)).isSgxEnabled();
        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        verifyNoInteractions(lasServicesManager);
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceTeeEnclaveConfigurationIsNull() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID))
                .thenThrow(NullPointerException.class);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);
        assertThat(teePrerequisitesIssue)
                .containsExactly(new WorkflowError(PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION));
        verify(sgxService, times(2)).isSgxEnabled();
        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        verifyNoInteractions(lasServicesManager);
    }

    @Test
    void shouldTeePrerequisiteNotMetForTaskSinceCantPrepareTee() {
        when(sgxService.isSgxEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(null);
        when(lasServicesManager.startLasService(CHAIN_TASK_ID)).thenReturn(false);

        final List<WorkflowError> teePrerequisitesIssue =
                teeSconeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        assertThat(teePrerequisitesIssue)
                .containsExactly(new WorkflowError(TEE_PREPARATION_FAILED));

        verify(sgxService, times(2)).isSgxEnabled();
        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(teeServicesPropertiesService).getTeeServicesProperties(CHAIN_TASK_ID);
        verify(lasServicesManager).startLasService(CHAIN_TASK_ID);
    }
    // endregion

    // region getDockerEnv (pre, app, post)
    private void mockLas() {
        final LasService lasService = mock(LasService.class);
        when(lasService.getUrl()).thenReturn(LAS_URL);
        when(lasService.getSconeConfig()).thenReturn(sconeConfig);
        when(lasServicesManager.getLas(CHAIN_TASK_ID)).thenReturn(lasService);
    }

    @Test
    void shouldBuildPreComputeDockerEnv() {
        ReflectionTestUtils.setField(teeSconeService, "teeSessions", Map.of(CHAIN_TASK_ID, SESSION));
        mockLas();
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(preComputeProperties.getHeapSizeInBytes()).thenReturn(HEAP_SIZE);

        assertThat(teeSconeService.buildPreComputeDockerEnv(TASK_DESCRIPTION))
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
        ReflectionTestUtils.setField(teeSconeService, "teeSessions", Map.of(CHAIN_TASK_ID, SESSION));
        mockLas();
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        assertThat(teeSconeService.buildComputeDockerEnv(TASK_DESCRIPTION))
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
        ReflectionTestUtils.setField(teeSconeService, "teeSessions", Map.of(CHAIN_TASK_ID, SESSION));
        mockLas();
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(properties);
        when(properties.getPostComputeProperties()).thenReturn(postComputeProperties);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(HEAP_SIZE);

        assertThat(teeSconeService.buildPostComputeDockerEnv(TASK_DESCRIPTION))
                .isEqualTo(List.of(
                        "SCONE_CAS_ADDR=" + CAS_URL,
                        "SCONE_LAS_ADDR=" + LAS_URL,
                        "SCONE_CONFIG_ID=" + SESSION_ID + "/post-compute",
                        "SCONE_HEAP=" + HEAP_SIZE,
                        "SCONE_LOG=" + LOG_LEVEL,
                        "SCONE_VERSION=" + 1));
    }
    // endregion

    // region TEE sessions cache
    private void prefillTeeSessionsCache(final Map<String, TeeSessionGenerationResponse> teeSessions) {
        teeSessions.put("taskId1", new TeeSessionGenerationResponse("sessionId1", "sessionUrl1"));
        teeSessions.put("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2"));
        ReflectionTestUtils.setField(teeSconeService, "teeSessions", teeSessions);
    }

    @Test
    void shouldNotModifyCacheWhenNoSessionInCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions);
        teeSconeService.purgeTask("taskId3");
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
        teeSconeService.purgeTask("taskId1");
        assertThat(teeSessions)
                .usingRecursiveComparison()
                .isEqualTo(Map.of("taskId2", new TeeSessionGenerationResponse("sessionId2", "sessionUrl2")));
    }

    @Test
    void shouldRemoveAllTeeSessionsFromCache() {
        final Map<String, TeeSessionGenerationResponse> teeSessions = new ConcurrentHashMap<>();
        prefillTeeSessionsCache(teeSessions);
        teeSconeService.purgeAllTasksData();
        assertThat(teeSessions).isEmpty();
    }
    // end region
}
