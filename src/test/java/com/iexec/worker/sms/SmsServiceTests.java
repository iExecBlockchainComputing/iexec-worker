package com.iexec.worker.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.SmsClient;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

public class SmsServiceTests {

    public static final String CHAIN_TASK_ID = "0xabc";
    public static final String SESSION_ID = "randomSessionId";

    @Rule
    public TemporaryFolder jUnitTemporaryFolder = new TemporaryFolder();

    @Mock
    private CredentialsService credentialsService;
    @Mock
    private SmsClient smsClient;

    @InjectMocks
    private SmsService smsService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCreateTeeSession() throws IOException {
        Signature signatureStub = new Signature("random-signature");
        WorkerpoolAuthorization workerpoolAuthorization = mock(WorkerpoolAuthorization.class);
        when(credentialsService.hashAndSignMessage(workerpoolAuthorization.getHash()))
                .thenReturn(signatureStub);
        when(smsClient.createTeeSession(signatureStub.getValue(), workerpoolAuthorization))
                .thenReturn(ResponseEntity.ok(SESSION_ID));

        String returnedSessionId = smsService.createTeeSession(workerpoolAuthorization);
        assertThat(returnedSessionId).isEqualTo(SESSION_ID);
    }
}