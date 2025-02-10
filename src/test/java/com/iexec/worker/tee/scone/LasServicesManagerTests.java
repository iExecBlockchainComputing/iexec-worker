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

package com.iexec.worker.tee.scone;

import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LasServicesManagerTests {
    private static final String CHAIN_TASK_ID_1 = "chainTaskId1";
    private static final String CHAIN_TASK_ID_2 = "chainTaskId2";
    private static final String TEE_FRAMEWORK_VERSION = "v5";

    private static final String LAS_IMAGE_URI_1 = "lasImage1";
    private static final String LAS_IMAGE_URI_2 = "lasImage2";
    private static final String WORKER_WALLET_ADDRESS = "0x2D29bfBEc903479fe4Ba991918bAB99B494f2bEf";

    private static final SconeServicesProperties PROPERTIES_1 = new SconeServicesProperties(
            TEE_FRAMEWORK_VERSION,
            null,
            null,
            LAS_IMAGE_URI_1);
    private static final SconeServicesProperties PROPERTIES_2 = new SconeServicesProperties(
            TEE_FRAMEWORK_VERSION,
            null,
            null,
            LAS_IMAGE_URI_2);
    private static final SconeServicesProperties PROPERTIES_3 = new SconeServicesProperties(
            TEE_FRAMEWORK_VERSION,
            null,
            null,
            LAS_IMAGE_URI_1);

    @Mock
    SmsClient mockedSmsClient;
    @Mock
    LasService mockedLasService1;
    @Mock
    LasService mockedLasService2;

    @Mock
    SconeConfiguration sconeConfiguration;
    @Mock
    TeeServicesPropertiesService teeServicesPropertiesService;
    @Mock
    WorkerConfigurationService workerConfigService;
    @Mock
    SgxService sgxService;
    @Mock
    DockerService dockerService;
    @InjectMocks
    @Spy
    LasServicesManager lasServicesManager;

    private void stubLasService1() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(lasServicesManager.createLasService(LAS_IMAGE_URI_1)).thenReturn(mockedLasService1);
    }

    private void stubLasService2() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_2)).thenReturn(PROPERTIES_2);
        when(lasServicesManager.createLasService(LAS_IMAGE_URI_2)).thenReturn(mockedLasService2);
    }

    // region startLasService
    @Test
    void shouldStartLasServiceWhenLasNotYetCreated() {
        stubLasService1();
        when(mockedLasService1.start()).thenReturn(true);

        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotStartLasServiceWhenAlreadyStarted() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(mockedLasService1);
        when(mockedLasService1.isStarted()).thenReturn(true);

        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verifyNoInteractions(teeServicesPropertiesService, mockedSmsClient);
    }

    @Test
    void shouldStartLasServiceWhenLasCreatedButNotStarted() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(mockedLasService1);
        when(mockedLasService1.isStarted()).thenReturn(false);
        when(mockedLasService1.start()).thenReturn(true);

        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verifyNoInteractions(teeServicesPropertiesService, mockedSmsClient);
    }

    @Test
    void shouldStartTwoLasServicesForDifferentLasImageUri() {
        stubLasService1();
        stubLasService2();
        when(mockedLasService1.start()).thenReturn(true);
        when(mockedLasService2.start()).thenReturn(true);

        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        assertNotEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
        assertNotNull(lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldStartOnlyOneLasServiceForSameLasImageUri() {
        stubLasService1();
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_2)).thenReturn(PROPERTIES_3);
        when(mockedLasService1.start()).thenReturn(true);

        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        assertEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
        assertNotNull(lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotStartLasServiceSinceMissingTeeWorkflowConfiguration() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(null);

        assertFalse(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verify(mockedLasService1, times(0)).start();
    }

    @Test
    void shouldNotStartLasServiceSinceMissingImageName() {
        SconeServicesProperties properties = new SconeServicesProperties(TEE_FRAMEWORK_VERSION, null, null, "");
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(properties);
        assertFalse(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
    }
    // endregion

    // region stopLasServices
    @Test
    void shouldStopLasServices() {
        // Two steps:
        // 1- Filling the LAS map with lasServicesManager.startLasService(...)
        //    and setting their isStarted values to true
        // 2- Calling lasServicesManager.stopLasServices and checking isStarted is back to false
        final Map<String, Boolean> areStarted = new HashMap<>(Map.of(
                CHAIN_TASK_ID_1, false,
                CHAIN_TASK_ID_2, false
        ));

        startLasService(CHAIN_TASK_ID_1, PROPERTIES_1, mockedLasService1, areStarted);
        startLasService(CHAIN_TASK_ID_2, PROPERTIES_2, mockedLasService2, areStarted);

        lasServicesManager.stopLasServices();
        assertFalse(areStarted.get(CHAIN_TASK_ID_1));
        assertFalse(areStarted.get(CHAIN_TASK_ID_2));
    }

    private void startLasService(String chainTaskId,
                                 SconeServicesProperties config,
                                 LasService lasService,
                                 Map<String, Boolean> areStarted) {
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(config);
        when(lasServicesManager.createLasService(config.getLasImage())).thenReturn(lasService);

        when(lasService.start()).then(invocation -> {
            areStarted.put(chainTaskId, true);
            return true;
        });
        doAnswer(invocation -> areStarted.put(chainTaskId, false)).when(lasService).stopAndRemoveContainer();

        lasServicesManager.startLasService(chainTaskId);
        assertTrue(areStarted.get(chainTaskId));
    }
    // endregion

    // region createLasContainerName
    @Test
    void shouldCreateLasContainerNameWithProperCharLength() {
        final LasServicesManager lasServicesManagerWithWalletAddress = new LasServicesManager(
                sconeConfiguration, teeServicesPropertiesService, workerConfigService,
                sgxService, dockerService, WORKER_WALLET_ADDRESS);
        final String createdLasContainerName = lasServicesManagerWithWalletAddress.createLasContainerName();
        assertTrue(
                createdLasContainerName.length() < 64);
        //more checks about
        final String expectedPrefix = "iexec-las";
        assertTrue(createdLasContainerName.startsWith(expectedPrefix));
        assertTrue(createdLasContainerName
                .contains(WORKER_WALLET_ADDRESS));
        int minimumLength = String.format("%s-%s-",
                expectedPrefix, WORKER_WALLET_ADDRESS).length();
        assertTrue(createdLasContainerName.length() > minimumLength);
    }

    @Test
    void shouldCreateLasContainerNameWithRandomness() {
        final LasServicesManager lasServicesManagerWithWalletAddress = new LasServicesManager(
                sconeConfiguration, teeServicesPropertiesService, workerConfigService,
                sgxService, dockerService, WORKER_WALLET_ADDRESS);
        //calling twice should return different values
        assertNotEquals(lasServicesManagerWithWalletAddress.createLasContainerName(),
                lasServicesManagerWithWalletAddress.createLasContainerName());
    }
    // endregion

    // region getLas
    @Test
    void shouldGetLas() {
        stubLasService1();
        when(mockedLasService1.start()).thenReturn(true);

        lasServicesManager.startLasService(CHAIN_TASK_ID_1); // Filling the LAS map

        assertEquals(mockedLasService1, lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotGetLasSinceNoLasInMap() {
        assertNull(lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotGetLasSinceNoLasInMapForGivenTask() {
        stubLasService1();
        when(mockedLasService1.start()).thenReturn(true);

        lasServicesManager.startLasService(CHAIN_TASK_ID_1); // Filling the LAS map

        assertNull(lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }
    // endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        final Map<String, LasService> chainTaskIdToLasService = new HashMap<>();
        chainTaskIdToLasService.put(CHAIN_TASK_ID_1, mockedLasService1);
        ReflectionTestUtils.setField(lasServicesManager, "chainTaskIdToLasService", chainTaskIdToLasService);

        assertTrue(lasServicesManager.purgeTask(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldPurgeTaskEvenThoughEmptyMap() {
        assertTrue(lasServicesManager.purgeTask(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldPurgeTaskEvenThoughNoMatchingTaskId() {
        final Map<String, LasService> chainTaskIdToLasService = new HashMap<>();
        chainTaskIdToLasService.put(CHAIN_TASK_ID_2, mockedLasService2);
        ReflectionTestUtils.setField(lasServicesManager, "chainTaskIdToLasService", chainTaskIdToLasService);

        assertTrue(lasServicesManager.purgeTask(CHAIN_TASK_ID_1));
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksData() {
        assertDoesNotThrow(lasServicesManager::purgeAllTasksData);
    }
    // endregion
}
