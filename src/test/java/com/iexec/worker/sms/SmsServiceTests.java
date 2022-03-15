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
import com.iexec.sms.api.SmsClient;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.PublicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmsServiceTests {

    public static final String CHAIN_TASK_ID = "0xabc";
    public static final String SESSION_ID = "randomSessionId";

    @Mock
    private CredentialsService credentialsService;
    @Mock
    private PublicConfigurationService configurationService;
    @Mock
    private SmsClient smsClient;

    private SmsService smsService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(configurationService.getSmsURL()).thenReturn("http://localhost");
        smsService = new SmsService(credentialsService, configurationService);
        ReflectionTestUtils.setField(smsService, "smsClient", smsClient);
    }

    @Test
    void shouldCreateTeeSession() {
        Signature signatureStub = new Signature("random-signature");
        WorkerpoolAuthorization workerpoolAuthorization = mock(WorkerpoolAuthorization.class);
        when(credentialsService.hashAndSignMessage(workerpoolAuthorization.getHash()))
                .thenReturn(signatureStub);
        when(smsClient.generateTeeSession(signatureStub.getValue(), workerpoolAuthorization))
                .thenReturn(SESSION_ID);

        String returnedSessionId = smsService.createTeeSession(workerpoolAuthorization);
        assertThat(returnedSessionId).isEqualTo(SESSION_ID);
    }
}