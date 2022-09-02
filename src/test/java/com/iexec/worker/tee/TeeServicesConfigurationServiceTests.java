package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.config.GramineServicesConfiguration;
import com.iexec.sms.api.config.TeeAppConfiguration;
import com.iexec.sms.api.config.TeeServicesConfiguration;
import com.iexec.worker.docker.DockerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeeServicesConfigurationServiceTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final TaskDescription TASK_DESCRIPTION = TaskDescription
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .teeEnclaveProvider(TeeEnclaveProvider.GRAMINE)
            .build();
    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final long PRE_COMPUTE_HEAP_SIZE = 1024L;
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private static final String POST_COMPUTE_IMAGE = "postComputeImage";
    private static final long POST_COMPUTE_HEAP_SIZE = 1024L;
    private static final String POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private static final GramineServicesConfiguration GRAMINE_CONFIG = new GramineServicesConfiguration(
            new TeeAppConfiguration(PRE_COMPUTE_IMAGE, "", PRE_COMPUTE_ENTRYPOINT, PRE_COMPUTE_HEAP_SIZE),
            new TeeAppConfiguration(POST_COMPUTE_IMAGE, "", POST_COMPUTE_ENTRYPOINT, POST_COMPUTE_HEAP_SIZE)
    );

    @Mock
    DockerClientInstance dockerClient;
    @Mock
    SmsClient smsClient;
    @Mock
    SmsClientProvider smsClientProvider;
    @Mock
    DockerService dockerService;
    @Mock
    IexecHubAbstractService iexecHubService;

    @Spy
    @InjectMocks
    TeeServicesConfigurationService teeServicesConfigurationService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        when(dockerService.getClient(any())).thenReturn(dockerClient);
    }

    // region retrieveTeeServicesConfiguration
    @Test
    void shouldRetrieveTeeServicesConfiguration() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.getTeeEnclaveProvider()).thenReturn(TeeEnclaveProvider.GRAMINE);
        when(smsClient.getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE)).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(true);

        final TeeServicesConfiguration teeServicesConfiguration = assertDoesNotThrow(
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));

        TeeAppConfiguration preComputeConfiguration = teeServicesConfiguration.getPreComputeConfiguration();
        TeeAppConfiguration postComputeConfiguration = teeServicesConfiguration.getPostComputeConfiguration();

        assertNotNull(preComputeConfiguration);
        assertNotNull(postComputeConfiguration);

        assertEquals(PRE_COMPUTE_IMAGE, preComputeConfiguration.getImage());
        assertEquals(PRE_COMPUTE_HEAP_SIZE, preComputeConfiguration.getHeapSize());
        assertEquals(PRE_COMPUTE_ENTRYPOINT, preComputeConfiguration.getEntrypoint());
        assertEquals(POST_COMPUTE_IMAGE, postComputeConfiguration.getImage());
        assertEquals(POST_COMPUTE_HEAP_SIZE, postComputeConfiguration.getHeapSize());
        assertEquals(POST_COMPUTE_ENTRYPOINT, postComputeConfiguration.getEntrypoint());

        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(smsClient).getTeeEnclaveProvider();
        verify(smsClient).getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenWrongTeeEnclaveProvider() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.getTeeEnclaveProvider()).thenReturn(TeeEnclaveProvider.SCONE);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("SMS is configured for another TEE enclave provider" +
                " [chainTaskId:" + CHAIN_TASK_ID +
                ", requiredProvider:" + TeeEnclaveProvider.GRAMINE +
                ", actualProvider:" + TeeEnclaveProvider.SCONE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(smsClient).getTeeEnclaveProvider();
        verify(smsClient, times(0)).getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE);
        verify(dockerClient, times(0)).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenNoConfigRetrieved() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.getTeeEnclaveProvider()).thenReturn(TeeEnclaveProvider.GRAMINE);
        when(smsClient.getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE)).thenReturn(null);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Missing TEE services configuration [chainTaskId:" + CHAIN_TASK_ID +"]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(smsClient).getTeeEnclaveProvider();
        verify(smsClient).getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE);
        verify(dockerClient, times(0)).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPreComputeImage() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.getTeeEnclaveProvider()).thenReturn(TeeEnclaveProvider.GRAMINE);
        when(smsClient.getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE)).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", preComputeImage:" + PRE_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(smsClient).getTeeEnclaveProvider();
        verify(smsClient).getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotRetrieveTeeServicesConfigurationWhenFailedToDownloadPostComputeImage() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.getTeeEnclaveProvider()).thenReturn(TeeEnclaveProvider.GRAMINE);
        when(smsClient.getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE)).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", postComputeImage:" + POST_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(TASK_DESCRIPTION);
        verify(smsClient).getTeeEnclaveProvider();
        verify(smsClient).getTeeServicesConfiguration(TeeEnclaveProvider.GRAMINE);
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(POST_COMPUTE_IMAGE);
    }
    // endregion
}