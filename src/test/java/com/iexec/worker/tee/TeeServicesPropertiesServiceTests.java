package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeFramework;
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
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final TaskDescription TASK_DESCRIPTION = TaskDescription
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .teeFramework(TeeFramework.GRAMINE)
            .build();
    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final long PRE_COMPUTE_HEAP_SIZE = 1024L;
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private static final String POST_COMPUTE_IMAGE = "postComputeImage";
    private static final long POST_COMPUTE_HEAP_SIZE = 1024L;
    private static final String POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private static final GramineServicesProperties GRAMINE_PROPERTIES = new GramineServicesProperties(
            new TeeAppProperties(PRE_COMPUTE_IMAGE, "", PRE_COMPUTE_ENTRYPOINT, PRE_COMPUTE_HEAP_SIZE),
            new TeeAppProperties(POST_COMPUTE_IMAGE, "", POST_COMPUTE_ENTRYPOINT, POST_COMPUTE_HEAP_SIZE)
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
        when(smsClient.getTeeServicesProperties(TeeFramework.GRAMINE)).thenReturn(GRAMINE_PROPERTIES);
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
        verify(smsClient).getTeeServicesProperties(TeeFramework.GRAMINE);
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
        verify(smsClient, times(0)).getTeeServicesProperties(TeeFramework.GRAMINE);
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
        when(smsClient.getTeeServicesProperties(TeeFramework.GRAMINE)).thenReturn(null);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Missing TEE services properties [chainTaskId:" + CHAIN_TASK_ID +"]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesProperties(TeeFramework.GRAMINE);
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
        when(smsClient.getTeeServicesProperties(TeeFramework.GRAMINE)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(PRE_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", preComputeImage:" + PRE_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesProperties(TeeFramework.GRAMINE);
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
        when(smsClient.getTeeServicesProperties(TeeFramework.GRAMINE)).thenReturn(GRAMINE_PROPERTIES);
        when(dockerClient.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerClient.isImagePresent(POST_COMPUTE_IMAGE)).thenReturn(false);
        when(dockerClient.pullImage(POST_COMPUTE_IMAGE)).thenReturn(false);

        TeeServicesPropertiesCreationException exception = assertThrows(TeeServicesPropertiesCreationException.class,
                () -> teeServicesPropertiesService.retrieveTeeServicesProperties(CHAIN_TASK_ID));
        assertEquals("Failed to download image " +
                "[chainTaskId:" + CHAIN_TASK_ID +", postComputeImage:" + POST_COMPUTE_IMAGE + "]", exception.getMessage());

        verify(smsService).getSmsClient(CHAIN_TASK_ID);
        verify(smsClient).getTeeFramework();
        verify(smsClient).getTeeServicesProperties(TeeFramework.GRAMINE);
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