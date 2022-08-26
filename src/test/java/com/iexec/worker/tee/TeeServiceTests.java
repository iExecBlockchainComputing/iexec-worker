package com.iexec.worker.tee;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.worker.sgx.SgxService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeeServiceTests {
    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    SgxService sgxService;
    @Mock
    SmsClientProvider smsClientProvider;
    @Mock
    TeeWorkflowConfigurationService teeWorkflowConfigurationService;

    @Spy
    @InjectMocks
    TeeServiceMock teeService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region isTeeEnabled
    @Test
    void shouldTeeBeEnabled() {
        when(sgxService.isSgxEnabled()).thenReturn(true);

        assertTrue(teeService.isTeeEnabled());

        verify(sgxService).isSgxEnabled();
    }

    @Test
    void shouldTeeNotBeEnabled() {
        when(sgxService.isSgxEnabled()).thenReturn(false);

        assertFalse(teeService.isTeeEnabled());

        verify(sgxService).isSgxEnabled();
    }
    //

    // region areTeePrerequisitesMetForTask
    @Test
    void shouldTeePrerequisitesBeMet() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(null);
        when(teeWorkflowConfigurationService.getOrCreateTeeWorkflowConfiguration(CHAIN_TASK_ID)).thenReturn(null);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isEmpty());
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceTeeNotEnabled() {
        when(teeService.isTeeEnabled()).thenReturn(false);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(TEE_NOT_SUPPORTED, teePrerequisitesIssue.get());
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceSmsClientCantBeLoaded() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenThrow(SmsClientCreationException.class);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(UNKNOWN_SMS, teePrerequisitesIssue.get());
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceTeeWorkflowConfigurationCantBeLoaded() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(null);
        when(teeWorkflowConfigurationService.getOrCreateTeeWorkflowConfiguration(CHAIN_TASK_ID)).thenThrow(SmsClientCreationException.class);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(GET_TEE_WORKFLOW_CONFIGURATION_FAILED, teePrerequisitesIssue.get());
    }
    // endregion
}