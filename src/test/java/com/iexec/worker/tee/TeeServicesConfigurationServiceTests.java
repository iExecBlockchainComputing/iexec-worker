package com.iexec.worker.tee;

import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.config.GramineServicesConfiguration;
import com.iexec.sms.api.config.SconeServicesConfiguration;
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

    @Spy
    @InjectMocks
    TeeServicesConfigurationService teeServicesConfigurationService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        when(dockerService.getClient(any())).thenReturn(dockerClient);
    }

    @Test
    void shouldBuildTeeWorkflowConfiguration() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getGramineServicesConfiguration()).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(true);

        final TeeServicesConfiguration teeWorkflowConfiguration = assertDoesNotThrow(
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));

        TeeAppConfiguration preComputeConfiguration = teeWorkflowConfiguration.getPreComputeConfiguration();
        TeeAppConfiguration postComputeConfiguration = teeWorkflowConfiguration.getPostComputeConfiguration();

        assertNotNull(preComputeConfiguration);
        assertNotNull(postComputeConfiguration);

        assertEquals(PRE_COMPUTE_IMAGE, preComputeConfiguration.getImage());
        assertEquals(PRE_COMPUTE_HEAP_SIZE, preComputeConfiguration.getHeapSize());
        assertEquals(PRE_COMPUTE_ENTRYPOINT, preComputeConfiguration.getEntrypoint());
        assertEquals(POST_COMPUTE_IMAGE, postComputeConfiguration.getImage());
        assertEquals(POST_COMPUTE_HEAP_SIZE, postComputeConfiguration.getHeapSize());
        assertEquals(POST_COMPUTE_ENTRYPOINT, postComputeConfiguration.getEntrypoint());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getGramineServicesConfiguration();
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenNoConfigRetrieved() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getGramineServicesConfiguration()).thenReturn(null);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Missing tee workflow configuration [chainTaskId:" + CHAIN_TASK_ID +"]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getGramineServicesConfiguration();
        verify(dockerClient, times(0)).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenFailedToDownloadPreComputeImage() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getGramineServicesConfiguration()).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", preComputeImage:" + PRE_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getGramineServicesConfiguration();
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenFailedToDownloadPostComputeImage() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getGramineServicesConfiguration()).thenReturn(GRAMINE_CONFIG);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesConfigurationCreationException exception = assertThrows(TeeServicesConfigurationCreationException.class,
                () -> teeServicesConfigurationService.retrieveTeeServicesConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", postComputeImage:" + POST_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getGramineServicesConfiguration();
        verify(dockerClient).isImagePresent(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).isImagePresent(POST_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(POST_COMPUTE_IMAGE);
    }
}