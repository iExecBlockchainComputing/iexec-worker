package com.iexec.worker.tee;

import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
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

class TeeWorkflowConfigurationServiceTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final long PRE_COMPUTE_HEAP_SIZE = 1024L;
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private static final String POST_COMPUTE_IMAGE = "postComputeImage";
    private static final long POST_COMPUTE_HEAP_SIZE = 1024L;
    private static final String POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private static final TeeWorkflowSharedConfiguration SHARED_CONFIG = TeeWorkflowSharedConfiguration.builder()
            .preComputeImage(PRE_COMPUTE_IMAGE)
            .preComputeHeapSize(PRE_COMPUTE_HEAP_SIZE)
            .preComputeEntrypoint(PRE_COMPUTE_ENTRYPOINT)
            .postComputeImage(POST_COMPUTE_IMAGE)
            .postComputeHeapSize(POST_COMPUTE_HEAP_SIZE)
            .postComputeEntrypoint(POST_COMPUTE_ENTRYPOINT)
            .build();

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
    TeeWorkflowConfigurationService teeWorkflowConfigurationService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        when(dockerService.getClient(any())).thenReturn(dockerClient);
    }

    @Test
    void shouldBuildTeeWorkflowConfiguration() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeWorkflowConfiguration()).thenReturn(SHARED_CONFIG);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(true);

        final TeeWorkflowConfiguration teeWorkflowConfiguration = assertDoesNotThrow(
                () -> teeWorkflowConfigurationService.buildTeeWorkflowConfiguration(CHAIN_TASK_ID));
        assertEquals(PRE_COMPUTE_IMAGE, teeWorkflowConfiguration.getPreComputeImage());
        assertEquals(PRE_COMPUTE_HEAP_SIZE, teeWorkflowConfiguration.getPreComputeHeapSize());
        assertEquals(PRE_COMPUTE_ENTRYPOINT, teeWorkflowConfiguration.getPreComputeEntrypoint());
        assertEquals(POST_COMPUTE_IMAGE, teeWorkflowConfiguration.getPostComputeImage());
        assertEquals(POST_COMPUTE_HEAP_SIZE, teeWorkflowConfiguration.getPostComputeHeapSize());
        assertEquals(POST_COMPUTE_ENTRYPOINT, teeWorkflowConfiguration.getPostComputeEntrypoint());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getTeeWorkflowConfiguration();
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenNoConfigRetrieved() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeWorkflowConfiguration()).thenReturn(null);

        TeeWorkflowConfigurationCreationException exception = assertThrows(TeeWorkflowConfigurationCreationException.class,
                () -> teeWorkflowConfigurationService.buildTeeWorkflowConfiguration(CHAIN_TASK_ID));
        assertEquals("Missing tee workflow configuration [chainTaskId:" + CHAIN_TASK_ID +"]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getTeeWorkflowConfiguration();
        verify(dockerClient, times(0)).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenFailedToDownloadPreComputeImage() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeWorkflowConfiguration()).thenReturn(SHARED_CONFIG);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);

        TeeWorkflowConfigurationCreationException exception = assertThrows(TeeWorkflowConfigurationCreationException.class,
                () -> teeWorkflowConfigurationService.buildTeeWorkflowConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download pre-compute image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", preComputeImage:" + PRE_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getTeeWorkflowConfiguration();
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient, times(0)).pullImage(POST_COMPUTE_IMAGE);
    }

    @Test
    void shouldNotBuildTeeWorkflowConfigurationWhenFailedToDownloadPostComputeImage() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.getTeeWorkflowConfiguration()).thenReturn(SHARED_CONFIG);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        TeeWorkflowConfigurationCreationException exception = assertThrows(TeeWorkflowConfigurationCreationException.class,
                () -> teeWorkflowConfigurationService.buildTeeWorkflowConfiguration(CHAIN_TASK_ID));
        assertEquals("Failed to download post-compute image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", postComputeImage:" + POST_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsClientProvider).getOrCreateSmsClientForTask(CHAIN_TASK_ID);
        verify(smsClient).getTeeWorkflowConfiguration();
        verify(dockerClient).pullImage(PRE_COMPUTE_IMAGE);
        verify(dockerClient).pullImage(POST_COMPUTE_IMAGE);
    }
}