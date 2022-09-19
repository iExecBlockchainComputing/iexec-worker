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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.web.ApiResponseBody;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.chain.CredentialsService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;

import static org.mockito.Mockito.*;

class SmsServiceTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static TaskDescription TASK_DESCRIPTION = TaskDescription
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .build();
    private final static String HASH = "hash";
    private final static WorkerpoolAuthorization WORKERPOOL_AUTHORIZATION = spy(WorkerpoolAuthorization
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .build());

    private final static TeeSessionGenerationResponse SESSION = mock(TeeSessionGenerationResponse.class);

    private static final String SIGNATURE = "random-signature";
    private static final String smsUrl = "smsUrl";

    @Mock
    private CredentialsService credentialsService;
    @Mock
    private SmsClient smsClient;
    @Mock
    private SmsClientProvider smsClientProvider;
    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        doReturn(HASH).when(WORKERPOOL_AUTHORIZATION).getHash();
    }

    @Test
    void shouldCreateTeeSession() throws TeeSessionGenerationException {
        Signature signatureStub = new Signature(SIGNATURE);
        when(credentialsService.hashAndSignMessage(WORKERPOOL_AUTHORIZATION.getHash()))
                .thenReturn(signatureStub);
        when(smsClientProvider.getSmsClient(smsUrl)).thenReturn(smsClient);
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(smsClient.generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION))
                .thenReturn(ApiResponseBody.<TeeSessionGenerationResponse, TeeSessionGenerationError>builder().data(SESSION).build());

        TeeSessionGenerationResponse returnedSessionId = smsService.createTeeSession(WORKERPOOL_AUTHORIZATION);
        Assertions.assertThat(returnedSessionId).isEqualTo(SESSION);
        verify(smsClient).generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION);
    }

    @Test
    void shouldNotCreateTeeSessionOnFeignException() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        final byte[] responseBody = mapper.writeValueAsBytes(ApiResponseBody.<Void, TeeSessionGenerationError>builder().error(TeeSessionGenerationError.NO_SESSION_REQUEST).build());
        final Request request = Request.create(Request.HttpMethod.GET, "url",
                new HashMap<>(), null, new RequestTemplate());

        Signature signatureStub = new Signature(SIGNATURE);
        when(credentialsService.hashAndSignMessage(WORKERPOOL_AUTHORIZATION.getHash()))
                .thenReturn(signatureStub);
        when(smsClientProvider.getSmsClient(smsUrl)).thenReturn(smsClient);
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(smsClient.generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION))
                .thenThrow(new FeignException.InternalServerError("", request, responseBody, null ));   //FIXME

        final TeeSessionGenerationException exception = Assertions.catchThrowableOfType(() -> smsService.createTeeSession(WORKERPOOL_AUTHORIZATION), TeeSessionGenerationException.class);
        Assertions.assertThat(exception.getTeeSessionGenerationError()).isEqualTo(TeeSessionGenerationError.NO_SESSION_REQUEST);
        verify(smsClient).generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION);
    }
}
