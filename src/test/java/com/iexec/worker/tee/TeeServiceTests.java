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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeeServiceTests {
    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    SgxService sgxService;
    @Mock
    SmsService smsService;
    @Mock
    SmsClient smsClient;
    @Mock
    TeeServicesPropertiesService teeServicesPropertiesService;

    @Spy
    @InjectMocks
    TeeServiceMock teeService;

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
    // endregion

    // region areTeePrerequisitesMetForTask
    @Test
    void shouldTeePrerequisitesBeMet() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(null);

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
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenThrow(SmsClientCreationException.class);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(UNKNOWN_SMS, teePrerequisitesIssue.get());
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceTeeEnclaveConfigurationIsNull() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenThrow(IllegalArgumentException.class);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION, teePrerequisitesIssue.get());
    }

    @Test
    void shouldTeePrerequisitesNotBeMetSinceTeeWorkflowConfigurationCantBeLoaded() {
        when(teeService.isTeeEnabled()).thenReturn(true);
        when(smsService.getSmsClient(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenThrow(RuntimeException.class);

        Optional<ReplicateStatusCause> teePrerequisitesIssue = teeService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID);

        Assertions.assertTrue(teePrerequisitesIssue.isPresent());
        Assertions.assertEquals(GET_TEE_SERVICES_CONFIGURATION_FAILED, teePrerequisitesIssue.get());
    }
    // endregion
}