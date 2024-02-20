/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.web.ApiResponseBody;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.security.Signature;
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

import static com.iexec.sms.secret.ReservedSecretKeyName.IEXEC_RESULT_IEXEC_IPFS_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsServiceTests {

    private static final String CHAIN_TASK_ID = "0x1";
    private static final String HASH = "hash";
    private static final String TOKEN = "token";
    private static final WorkerpoolAuthorization WORKERPOOL_AUTHORIZATION = spy(WorkerpoolAuthorization
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .enclaveChallenge("0x2")
            .workerWallet("0x3")
            .build());

    private static final TeeSessionGenerationResponse SESSION = mock(TeeSessionGenerationResponse.class);

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
        when(smsClientProvider.getSmsClient(smsUrl)).thenReturn(smsClient);
    }

    // region getSmsClient
    @Test
    void shouldGetSmsClient() {
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        assertThat(smsService.getSmsClient(CHAIN_TASK_ID)).isEqualTo(smsClient);
    }

    @Test
    void shouldNotAndGetSmsClientIfNoSmsUrlForTask() {
        // no SMS URL attached to taskId
        assertThrows(SmsClientCreationException.class, () -> smsService.getSmsClient(CHAIN_TASK_ID));
    }
    // endregion

    // region pushToken
    @Test
    void shouldPushToken() {
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(credentialsService.hashAndSignMessage(anyString())).thenReturn(new Signature(SIGNATURE));
        assertThat(smsService.pushToken(WORKERPOOL_AUTHORIZATION, TOKEN)).isTrue();
    }

    @Test
    void shouldNotPushTokenOnEmptySignature() {
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(credentialsService.hashAndSignMessage(anyString())).thenReturn(new Signature(""));
        assertThat(smsService.pushToken(WORKERPOOL_AUTHORIZATION, TOKEN)).isFalse();
    }

    @Test
    void shouldNotPushTokenOnFeignException() {
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(credentialsService.hashAndSignMessage(anyString())).thenReturn(new Signature(SIGNATURE));
        when(smsClient.setWeb2Secret(SIGNATURE, WORKERPOOL_AUTHORIZATION.getWorkerWallet(),
                IEXEC_RESULT_IEXEC_IPFS_TOKEN, TOKEN)).thenThrow(FeignException.InternalServerError.class);
        assertThat(smsService.pushToken(WORKERPOOL_AUTHORIZATION, TOKEN)).isFalse();
    }
    // endregion

    // region createTeeSession
    @Test
    void shouldCreateTeeSession() throws TeeSessionGenerationException {
        Signature signatureStub = new Signature(SIGNATURE);
        when(credentialsService.hashAndSignMessage(WORKERPOOL_AUTHORIZATION.getHash()))
                .thenReturn(signatureStub);
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(smsClient.generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION))
                .thenReturn(ApiResponseBody.<TeeSessionGenerationResponse, TeeSessionGenerationError>builder().data(SESSION).build());

        TeeSessionGenerationResponse returnedSessionId = smsService.createTeeSession(WORKERPOOL_AUTHORIZATION);
        assertThat(returnedSessionId).isEqualTo(SESSION);
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
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);
        when(smsClient.generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION))
                .thenThrow(new FeignException.InternalServerError("", request, responseBody, null));   //FIXME

        final TeeSessionGenerationException exception = Assertions.catchThrowableOfType(() -> smsService.createTeeSession(WORKERPOOL_AUTHORIZATION), TeeSessionGenerationException.class);
        assertThat(exception.getTeeSessionGenerationError()).isEqualTo(TeeSessionGenerationError.NO_SESSION_REQUEST);
        verify(smsClient).generateTeeSession(signatureStub.getValue(), WORKERPOOL_AUTHORIZATION);
    }
    // endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        // Attach sms URL to task
        smsService.attachSmsUrlToTask(CHAIN_TASK_ID, smsUrl);

        // Purging the task
        boolean purged = smsService.purgeTask(CHAIN_TASK_ID);
        assertTrue(purged);
    }

    @Test
    void shouldPurgeTaskEvenThoughTaskNeverAccessed() {
        assertTrue(smsService.purgeTask(CHAIN_TASK_ID));
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksData() {
        assertDoesNotThrow(smsService::purgeAllTasksData);
    }
    // endregion
}
