package com.iexec.worker.tee.scone;

import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class LasServicesManagerTests {
    private static final String CONTAINER_NAME = "containerName";

    private static final String CHAIN_TASK_ID_1 = "chainTaskId1";
    private static final String CHAIN_TASK_ID_2 = "chainTaskId2";

    private static final String LAS_IMAGE_URI_1 = "lasImage1";
    private static final String LAS_IMAGE_URI_2 = "lasImage2";

    private static final SconeServicesProperties PROPERTIES_1 = new SconeServicesProperties(
            null,
            null,
            LAS_IMAGE_URI_1);
    private static final SconeServicesProperties PROPERTIES_2 = new SconeServicesProperties(
            null,
            null,
            LAS_IMAGE_URI_2);
    private static final SconeServicesProperties PROPERTIES_3 = new SconeServicesProperties(
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

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        doReturn(CONTAINER_NAME).when(lasServicesManager).createLasContainerName();
        when(lasServicesManager.createLasService(LAS_IMAGE_URI_1)).thenReturn(mockedLasService1);
        when(lasServicesManager.createLasService(LAS_IMAGE_URI_2)).thenReturn(mockedLasService2);
    }

    // region startLasService
    @Test
    void shouldStartLasServiceWhenLasNotYetCreated() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(null);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotStartLasServiceWhenAlreadyStarted() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(mockedLasService1);
        when(mockedLasService1.isStarted()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verifyNoInteractions(teeServicesPropertiesService, mockedSmsClient);
    }

    @Test
    void shouldStartLasServiceWhenLasCreatedButNotStarted() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(mockedLasService1);
        when(mockedLasService1.isStarted()).thenReturn(false);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verifyNoInteractions(teeServicesPropertiesService, mockedSmsClient);
    }

    @Test
    void shouldStartTwoLasServicesForDifferentLasImageUri() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(null);
        when(lasServicesManager.getLas(CHAIN_TASK_ID_2)).thenReturn(null);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_2)).thenReturn(PROPERTIES_2);
        when(mockedLasService1.start()).thenReturn(true);
        when(mockedLasService2.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenCallRealMethod();
        when(lasServicesManager.getLas(CHAIN_TASK_ID_2)).thenCallRealMethod();
        Assertions.assertNotEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }

    @Test
    void shouldStartOnlyOneLasServiceForSameLasImageUri() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(null);
        when(lasServicesManager.getLas(CHAIN_TASK_ID_2)).thenReturn(null);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_2)).thenReturn(PROPERTIES_3);
        when(mockedSmsClient.getTeeServicesProperties(TeeEnclaveProvider.SCONE))
                .thenReturn(PROPERTIES_1)
                .thenReturn(PROPERTIES_3);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        Assertions.assertEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }

    @Test
    void shouldNotStartLasServiceSinceMissingTeeWorkflowConfiguration() {
        when(lasServicesManager.getLas(CHAIN_TASK_ID_1)).thenReturn(null);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(null);

        Assertions.assertFalse(lasServicesManager.startLasService(CHAIN_TASK_ID_1));

        verify(mockedLasService1, times(0)).start();
    }
    // endregion

    // region stopLasServices
    @Test
    void shouldStopLasServices() {
        // Two steps:
        // 1- Filling the LAS map with `lasServicesManager.startLasService(...)`
        //    and setting their `isStarted` values to `true`;
        // 2- Calling `lasServicesManager.stopLasServices` and checking `isStarted` is back to `false`.
        final Map<String, Boolean> areStarted = new HashMap<>(Map.of(
                CHAIN_TASK_ID_1, false,
                CHAIN_TASK_ID_2, false
        ));

        startLasService(CHAIN_TASK_ID_1, PROPERTIES_1, mockedLasService1, areStarted);
        startLasService(CHAIN_TASK_ID_2, PROPERTIES_2, mockedLasService2, areStarted);

        lasServicesManager.stopLasServices();
        Assertions.assertFalse(areStarted.get(CHAIN_TASK_ID_1));
        Assertions.assertFalse(areStarted.get(CHAIN_TASK_ID_2));
    }

    private void startLasService(String chainTaskId,
                                 SconeServicesProperties config,
                                 LasService lasService,
                                 Map<String, Boolean> areStarted) {
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(config);

        when(lasService.start()).then(invocation -> {
            areStarted.put(chainTaskId, true);
            return true;
        });
        doAnswer(invocation -> areStarted.put(chainTaskId, false)).when(lasService).stopAndRemoveContainer();

        lasServicesManager.startLasService(chainTaskId);
        Assertions.assertTrue(areStarted.get(chainTaskId));
    }
    // endregion

    // region createLasContainerName
    @Test
    void shouldCreateLasContainerNameWithProperCharLength() {
        LasServicesManager lasServicesManager = new LasServicesManager(
                sconeConfiguration, teeServicesPropertiesService, workerConfigService,
                sgxService, dockerService);
        ECKeyPair keyPair = ECKeyPair.create(new BigInteger(32, new Random()));
        when(workerConfigService.getWorkerWalletAddress())
                .thenReturn(Credentials.create(keyPair).getAddress());
        String createdLasContainerName = lasServicesManager.createLasContainerName();
        Assertions.assertTrue(
            createdLasContainerName.length() < 64);
        //more checks about
        String expectedPrefix = "iexec-las";
        Assertions.assertTrue(createdLasContainerName.startsWith(expectedPrefix));
        Assertions.assertTrue(createdLasContainerName
            .contains(workerConfigService.getWorkerWalletAddress()));
        int minimumLength = String.format("%s-%s-", 
            expectedPrefix, workerConfigService.getWorkerWalletAddress()).length();
        Assertions.assertTrue(createdLasContainerName.length() > minimumLength);
    }

    @Test
    void shouldCreateLasContainerNameWithRandomness() {
        LasServicesManager lasServicesManager = new LasServicesManager(
                sconeConfiguration, teeServicesPropertiesService, workerConfigService,
                sgxService, dockerService);
        ECKeyPair keyPair = ECKeyPair.create(new BigInteger(32, new Random()));
        when(workerConfigService.getWorkerWalletAddress())
                .thenReturn(Credentials.create(keyPair).getAddress());
        //calling twice should return different values
        Assertions.assertNotEquals(lasServicesManager.createLasContainerName(), 
            lasServicesManager.createLasContainerName());
    }
    // endregion

    // region getLas
    @Test
    void shouldGetLas() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(mockedLasService1.start()).thenReturn(true);

        lasServicesManager.startLasService(CHAIN_TASK_ID_1); // Filling the LAS map

        Assertions.assertEquals(mockedLasService1, lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotGetLasSinceNoLasInMap() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertNull(lasServicesManager.getLas(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldNotGetLasSinceNoLasInMapForGivenTask() {
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID_1)).thenReturn(PROPERTIES_1);
        when(mockedLasService1.start()).thenReturn(true);

        lasServicesManager.startLasService(CHAIN_TASK_ID_1); // Filling the LAS map

        Assertions.assertNull(lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }
    // endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        final Map<String, LasService> chainTaskIdToLasService =
                (Map<String, LasService>) ReflectionTestUtils.getField(lasServicesManager, "chainTaskIdToLasService");
        chainTaskIdToLasService.put(CHAIN_TASK_ID_1, mockedLasService1);

        assertTrue(lasServicesManager.purgeTask(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldPurgeTaskEvenThoughEmptyMap() {
        assertTrue(lasServicesManager.purgeTask(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldPurgeTaskEvenThoughNoMatchingTaskId() {
        final Map<String, LasService> chainTaskIdToLasService =
                (Map<String, LasService>) ReflectionTestUtils.getField(lasServicesManager, "chainTaskIdToLasService");
        chainTaskIdToLasService.put(CHAIN_TASK_ID_2, mockedLasService2);

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