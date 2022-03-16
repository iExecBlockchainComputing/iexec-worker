/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.sms;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.worker.chain.CredentialsService;
import feign.FeignException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

class SmsServiceTests {

    private static final String CAS_URL = "http://cas";
    private static final String SESSION_ID = "randomSessionId";
    private static final String SIGNATURE = "random-signature";

    @Mock
    private CredentialsService credentialsService;
    @Mock
    private SmsClient smsClient;
    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateTeeSession() {
        Signature signatureStub = new Signature(SIGNATURE);
        WorkerpoolAuthorization workerpoolAuthorization = mock(WorkerpoolAuthorization.class);
        when(credentialsService.hashAndSignMessage(workerpoolAuthorization.getHash()))
                .thenReturn(signatureStub);
        when(smsClient.generateTeeSession(signatureStub.getValue(), workerpoolAuthorization))
                .thenReturn(SESSION_ID);

        String returnedSessionId = smsService.createTeeSession(workerpoolAuthorization);
        Assertions.assertThat(returnedSessionId).isEqualTo(SESSION_ID);
    }

    @Test
    void shouldNotCreateTeeSessionOnFeignException() {
        Signature signatureStub = new Signature(SIGNATURE);
        WorkerpoolAuthorization workerpoolAuthorization = mock(WorkerpoolAuthorization.class);
        when(credentialsService.hashAndSignMessage(workerpoolAuthorization.getHash()))
                .thenReturn(signatureStub);
        when(smsClient.generateTeeSession(signatureStub.getValue(), workerpoolAuthorization))
                .thenThrow(FeignException.class)
                .thenThrow(RuntimeException.class);
        Assertions.assertThat(smsService.createTeeSession(workerpoolAuthorization)).isEmpty();
        Assertions.assertThat(smsService.createTeeSession(workerpoolAuthorization)).isEmpty();
        verify(smsClient, times(2))
                .generateTeeSession(signatureStub.getValue(), workerpoolAuthorization);
    }

    @Test
    void shouldGetSconeCasUrl() {
        when(smsClient.getSconeCasUrl()).thenReturn(CAS_URL);
        String sconeCasUrl = smsService.getSconeCasUrl();
        Assertions.assertThat(sconeCasUrl).isEqualTo(CAS_URL);
        verify(smsClient).getSconeCasUrl();
    }

    @Test
    void shouldNotGetSconeCasUrlOnException() {
        when(smsClient.getSconeCasUrl()).thenThrow(FeignException.class);
        Assertions.assertThat(smsService.getSconeCasUrl()).isEmpty();
        verify(smsClient).getSconeCasUrl();
    }

    @Test
    void shouldGetTeeWorkflowConfiguration() {
        TeeWorkflowSharedConfiguration teeWorkflowConfiguration = mock(TeeWorkflowSharedConfiguration.class);
        when(smsClient.getTeeWorkflowConfiguration()).thenReturn(teeWorkflowConfiguration);
        Assertions.assertThat(smsService.getTeeWorkflowConfiguration()).isEqualTo(teeWorkflowConfiguration);
        verify(smsClient).getTeeWorkflowConfiguration();
    }

    @Test
    void shouldGetTeeWorkflowConfigurationWhenAlreadyDefined() {
        TeeWorkflowSharedConfiguration teeWorkflowConfiguration = mock(TeeWorkflowSharedConfiguration.class);
        ReflectionTestUtils.setField(smsService, "teeWorkflowConfiguration", teeWorkflowConfiguration);
        Assertions.assertThat(smsService.getTeeWorkflowConfiguration()).isEqualTo(teeWorkflowConfiguration);
        verify(smsClient, never()).getTeeWorkflowConfiguration();
    }

    @Test
    void shouldNotGetTeeWorkflowConfigurationOnException() {
        when(smsClient.getTeeWorkflowConfiguration()).thenThrow(FeignException.class);
        Assertions.assertThat(smsService.getTeeWorkflowConfiguration()).isNull();
        verify(smsClient).getTeeWorkflowConfiguration();
    }

}
