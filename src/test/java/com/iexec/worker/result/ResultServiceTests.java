package com.iexec.worker.result;

import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ResultServiceTests {

    private final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker";

    @Mock private WorkerConfigurationService configurationService;
    @Mock private ResultRepoService resultRepoService;
    @Mock private CredentialsService credentialsService;
    @Mock private SmsService smsService;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetContentOfDeterministFileSinceByte32(){
        String chainTaskId = "bytes32";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String hash = resultService.getDeterministHashForTask(chainTaskId);
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo("0xda9a34f3846cc4434eb31ad870aaf47c8a123225732db003c0c19f3c3f6faa01");
    }

    @Test
    public void shouldGetHashOfDeterministFileSinceNotByte32(){
        String chainTaskId = "notBytes32";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String hash = resultService.getDeterministHashForTask(chainTaskId);
        // should not be equal to the content of the file since it is not a byte32
        assertThat(hash).isNotEqualTo("dummyRandomString");
    }

    @Test
    public void shouldGetCallbackDataFromFile(){
        String chainTaskId = "1234";
        String expected = "0x0000000000000000000000000000000000000000000000000000016a0caa81920000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000004982f5d9a7000000000000000000000000000000000000000000000000000000000000000094254432d5553442d390000000000000000000000000000000000000000000000";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo(expected);
    }

    @Test
    public void shouldNotGetCallbackDataSinceNotHexa(){
        String chainTaskId = "fake";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }

    @Test
    public void shouldNotGetCallbackDataSinceNoFile(){
        String chainTaskId = "fake2";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }

    @Test
    public void shouldNotGetCallbackDataSinceChainTaskIdMissing(){
        String chainTaskId = "";
        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }


}
