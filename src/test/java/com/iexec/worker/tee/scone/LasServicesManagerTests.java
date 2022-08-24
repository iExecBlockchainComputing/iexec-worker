package com.iexec.worker.tee.scone;

import com.iexec.sms.api.TeeWorkflowConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class LasServicesManagerTests {
    private static final String CONTAINER_NAME = "containerName";

    private static final String CHAIN_TASK_ID_1 = "chainTaskId1";
    private static final String CHAIN_TASK_ID_2 = "chainTaskId2";

    private static final String LAS_IMAGE_URI_1 = "lasImage1";
    private static final String LAS_IMAGE_URI_2 = "lasImage2";

    private static final TeeWorkflowConfiguration CONFIG_1 = TeeWorkflowConfiguration.builder()
            .lasImage(LAS_IMAGE_URI_1)
            .build();
    private static final TeeWorkflowConfiguration CONFIG_2 = TeeWorkflowConfiguration.builder()
            .lasImage(LAS_IMAGE_URI_2)
            .build();
    private static final TeeWorkflowConfiguration CONFIG_3 = TeeWorkflowConfiguration.builder()
            .lasImage(LAS_IMAGE_URI_1)
            .build();

    @Mock
    SmsClient mockedSmsClient;
    @Mock
    LasService mockedLasService1;
    @Mock
    LasService mockedLasService2;

    @Mock
    SconeConfiguration sconeConfiguration;
    @Mock
    SmsClientProvider smsClientProvider;
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

    @Test
    void shouldStartLasService() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID_1)).thenReturn(mockedSmsClient);
        when(mockedSmsClient.getTeeWorkflowConfiguration()).thenReturn(CONFIG_1);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
    }

    @Test
    void shouldStartTwoLasServicesForDifferentLasImageUri() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID_1)).thenReturn(mockedSmsClient);
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID_2)).thenReturn(mockedSmsClient);
        when(mockedSmsClient.getTeeWorkflowConfiguration())
                .thenReturn(CONFIG_1)
                .thenReturn(CONFIG_2);
        when(mockedLasService1.start()).thenReturn(true);
        when(mockedLasService2.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        Assertions.assertNotEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }

    @Test
    void shouldStartOnlyOneLasServiceForSameLasImageUri() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID_1)).thenReturn(mockedSmsClient);
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID_2)).thenReturn(mockedSmsClient);
        when(mockedSmsClient.getTeeWorkflowConfiguration())
                .thenReturn(CONFIG_1)
                .thenReturn(CONFIG_3);
        when(mockedLasService1.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_1));
        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID_2));

        Assertions.assertEquals(lasServicesManager.getLas(CHAIN_TASK_ID_1), lasServicesManager.getLas(CHAIN_TASK_ID_2));
    }
}