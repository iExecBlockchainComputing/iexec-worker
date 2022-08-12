package com.iexec.worker.tee.scone;

import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class LasServicesManagerTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String CONTAINER_NAME = "containerName";
    private static final TeeWorkflowSharedConfiguration CONFIG = TeeWorkflowSharedConfiguration.builder()
            .lasImage("lasImage")
            .build();

    @Mock
    SmsClient mockedSmsClient;
    @Mock
    LasService mockedLasService;

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

        when(lasServicesManager.createLasContainerName()).thenReturn(CONTAINER_NAME);
        when(lasServicesManager.createLasService(any(), any())).thenReturn(mockedLasService);
    }

    @Test
    void shouldStartLasService() {
        when(smsClientProvider.getSmsClientForTask(CHAIN_TASK_ID)).thenReturn(Optional.of(mockedSmsClient));
        when(mockedSmsClient.getTeeWorkflowConfiguration()).thenReturn(CONFIG);
        when(mockedLasService.start()).thenReturn(true);

        Assertions.assertTrue(lasServicesManager.startLasService(CHAIN_TASK_ID));
    }
}