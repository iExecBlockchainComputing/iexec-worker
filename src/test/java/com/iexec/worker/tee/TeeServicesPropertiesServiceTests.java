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

import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.chain.IexecHubAbstractService;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.GramineServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeeServicesPropertiesServiceTests {
    private static final String VERSION = "v5";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final TaskDescription TASK_DESCRIPTION = TaskDescription
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .teeFramework(TeeFramework.GRAMINE)
            .appEnclaveConfiguration(TeeEnclaveConfiguration.builder().version(VERSION).build())
            .build();
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
    IexecHubAbstractService iexecHubService;

    @Spy
    @InjectMocks
    TeeServicesPropertiesService teeServicesPropertiesService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        when(dockerService.getClient(any())).thenReturn(dockerClient);
    }

    // region retrieveTeeServicesConfiguration
    @Test
    void shouldRetrieveTeeServicesConfiguration() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(true);

        final TeeServicesProperties teeServicesProperties = assertDoesNotThrow(
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));

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
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenWrongTeeFramework() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.SCONE);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("SMS is configured for another TEE framework" +
                " [chainTaskId:" + CHAIN_TASK_ID +
                ", requiredFramework:" + TeeFramework.GRAMINE +
                ", actualFramework:" + TeeFramework.SCONE + "]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient, times(0)).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient, times(0)).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenNoConfigRetrieved() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(null);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Missing TEE services properties [chainTaskId:" + CHAIN_TASK_ID +"]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient, times(0)).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPreComputeImage() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", preComputeImage:" + PRE_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPostComputeImage() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.GRAMINE);
        when(smsClient.getTeeServicesPropertiesVersion(TeeFramework.GRAMINE, VERSION)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", postComputeImage:" + POST_COMPUTE_IMAGE + "]", exception.getMessage());

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