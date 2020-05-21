package com.iexec.worker.result;

import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;

import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ResultServiceTests {

    @Mock private WorkerConfigurationService workerConfigurationService;
//     @Mock private ResultRepoService resultRepoService;
    @Mock private CredentialsService credentialsService;
    @Mock private IexecHubService iexecHubService;
    @Mock private SmsService smsService;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }
}
