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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.GramineServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.workflow.WorkflowError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeeServicesPropertiesServiceTests {
    private static final String VERSION = "v5";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final long PRE_COMPUTE_HEAP_SIZE = 1024L;
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private static final String POST_COMPUTE_IMAGE = "postComputeImage";
    private static final long POST_COMPUTE_HEAP_SIZE = 1024L;
    private static final String POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private static final String TEE_FRAMEWORK_VERSION = "v5";

    private static final GramineServicesProperties GRAMINE_PROPERTIES = new GramineServicesProperties(
            TEE_FRAMEWORK_VERSION,
            TeeAppProperties.builder().image(PRE_COMPUTE_IMAGE).fingerprint("")
                    .entrypoint(PRE_COMPUTE_ENTRYPOINT).heapSizeInBytes(PRE_COMPUTE_HEAP_SIZE).build(),
            TeeAppProperties.builder().image(POST_COMPUTE_IMAGE).fingerprint("")
                    .entrypoint(POST_COMPUTE_ENTRYPOINT).heapSizeInBytes(POST_COMPUTE_HEAP_SIZE).build()
    );

    @Mock
    DockerClientInstance dockerClient;
    @Mock
    SmsClient smsClient;
    @Mock
    SmsService smsService;
    @Mock
    DockerService dockerService;
    @Mock
    IexecHubService iexecHubService;
    @Mock
    WorkerConfigurationService workerConfigurationService;

    @Spy
    @InjectMocks
    TeeServicesPropertiesService teeServicesPropertiesService;

    private final TaskDescription.TaskDescriptionBuilder taskDescriptionBuilder = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .teeFramework(TeeFramework.GRAMINE)
            .appEnclaveConfiguration(TeeEnclaveConfiguration.builder()
                    .version(VERSION)
                    .fingerprint("01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b")
                    .heapSize(2 * 1024 * 1024 * 1024L)
                    .entrypoint("python /app/app.py")
                    .build());

    // region retrieveTeeServicesConfiguration
    @Test
    void shouldRetrieveTeeServicesConfiguration() {
        when(dockerService.getClient(any())).thenReturn(dockerClient);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(true);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .isEmpty();
        final TeeServicesProperties teeServicesProperties = teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID);

        TeeAppProperties preComputeProperties = teeServicesProperties.getPreComputeProperties();
        TeeAppProperties postComputeProperties = teeServicesProperties.getPostComputeProperties();

        assertNotNull(preComputeProperties);
        assertNotNull(postComputeProperties);

        assertEquals(PRE_COMPUTE_IMAGE, preComputeProperties.getImage());
        assertEquals(PRE_COMPUTE_HEAP_SIZE, preComputeProperties.getHeapSizeInBytes());
        assertEquals(PRE_COMPUTE_ENTRYPOINT, preComputeProperties.getEntrypoint());
        assertEquals(POST_COMPUTE_IMAGE, postComputeProperties.getImage());
        assertEquals(POST_COMPUTE_HEAP_SIZE, postComputeProperties.getHeapSizeInBytes());
        assertEquals(POST_COMPUTE_ENTRYPOINT, postComputeProperties.getEntrypoint());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, never()).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, never()).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationForTdx() {
        final TaskDescription taskDescription = taskDescriptionBuilder.teeFramework(TeeFramework.TDX).build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID)).isEmpty();
        verifyNoInteractions(smsService, smsClient, dockerService, dockerClient);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenTeeEnclaveConfigurationIsNull() {
        final TaskDescription taskDescription = taskDescriptionBuilder.appEnclaveConfiguration(null).build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION));

        verifyNoInteractions(smsService, smsClient, dockerService, dockerClient);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceInvalidEnclaveConfiguration() {
        final TeeEnclaveConfiguration enclaveConfig = TeeEnclaveConfiguration.builder().build();
        final TaskDescription taskDescription = taskDescriptionBuilder
                .appEnclaveConfiguration(enclaveConfig)
                .build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        assertThat(enclaveConfig.getValidator().isValid()).isFalse();

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INVALID_ENCLAVE_CONFIGURATION));
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTooHighComputeHeapSize() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(1);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INVALID_ENCLAVE_HEAP_CONFIGURATION));
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenWrongTeeFramework() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.SCONE);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                        String.format("SMS is configured for another TEE framework [chainTaskId:%s, requiredFramework:%s, actualFramework:%s]",
                                CHAIN_TASK_ID, TeeFramework.GRAMINE, TeeFramework.SCONE)));

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient, never()).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verifyNoInteractions(dockerService, dockerClient);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenNoConfigRetrieved() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(null);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                        String.format("Missing TEE services properties [chainTaskId:%s]", CHAIN_TASK_ID)));

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verifyNoInteractions(dockerService, dockerClient);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPreComputeImage() {
        when(dockerService.getClient(any())).thenReturn(dockerClient);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(true);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                        String.format("Failed to download image [chainTaskId:%s, preComputeImage:%s]", CHAIN_TASK_ID, PRE_COMPUTE_IMAGE)));

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, never()).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPostComputeImage() {
        when(dockerService.getClient(any())).thenReturn(dockerClient);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescriptionBuilder.build());
        when(workerConfigurationService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        assertThat(teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID))
                .containsExactly(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                        String.format("Failed to download image [chainTaskId:%s, postComputeImage:%s]", CHAIN_TASK_ID, POST_COMPUTE_IMAGE)));

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(POST_COMPUTE_IMAGE);
    }
    // endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        final HashMap<String, TeeServicesProperties> propertiesForTask = new HashMap<>();
        propertiesForTask.put(CHAIN_TASK_ID, GRAMINE_PROPERTIES);
        ReflectionTestUtils.setField(teeServicesPropertiesService, "propertiesForTask", propertiesForTask);

        assertTrue(teeServicesPropertiesService.purgeTask(CHAIN_TASK_ID));
    }

    @Test
    void shouldPurgeTaskEvenThoughEmptyMap() {
        assertTrue(teeServicesPropertiesService.purgeTask(CHAIN_TASK_ID));
    }

    @Test
    void shouldPurgeTaskEvenThoughNoMatchingTaskId() {
        final HashMap<String, TeeServicesProperties> propertiesForTask = new HashMap<>();
        propertiesForTask.put(CHAIN_TASK_ID + "-wrong", GRAMINE_PROPERTIES);
        ReflectionTestUtils.setField(teeServicesPropertiesService, "propertiesForTask", propertiesForTask);

        assertTrue(teeServicesPropertiesService.purgeTask(CHAIN_TASK_ID));
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksData() {
        assertDoesNotThrow(teeServicesPropertiesService::purgeAllTasksData);
    }
    // endregion
}