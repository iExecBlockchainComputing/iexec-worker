package com.iexec.worker.result;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ResultServiceTests {

    private final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker";

    @Mock
    private WorkerConfigurationService configurationService;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetEnclaveSignature() throws IOException {
        String chainTaskId = "1234";
        String rExpected = "0x253554311f2793b72785a45eff4cbbe04c45d2e5a4d89057dfbc721e69b61d39";
        String sExpected = "0x6bdf554c8c12c158d12f08299afbe0d9c8533bf420a5d3f63ed9827047eab8d1";
        byte vExpected = 27;

        when(configurationService.getResultBaseDir()).thenReturn("./src/test/resources/tmp/test-worker");
        Optional<Signature> enclaveSignature = resultService.getEnclaveSignatureFromFile(chainTaskId);

        assertThat(enclaveSignature.isPresent()).isTrue();
        assertThat(enclaveSignature.get().getR()).isEqualTo(BytesUtils.stringToBytes(rExpected));
        assertThat(enclaveSignature.get().getS()).isEqualTo(BytesUtils.stringToBytes(sExpected));
        assertThat(enclaveSignature.get().getV()).isEqualTo(vExpected);
    }

    @Test
    public void shouldNotGetEnclaveSignatureSinceFileMissing() throws IOException {
        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources/fakefolder");
        Optional<Signature> enclaveSignature = resultService.getEnclaveSignatureFromFile("chainTaskId");

        assertThat(enclaveSignature.isPresent()).isFalse();
    }


    @Test
    public void shouldGetCallbackDataFromFile(){
        String chainTaskId = "1234";
        String expected = "0x0000000000000000000000000000000000000000000000000000016a0caa81920000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000004982f5d9a7000000000000000000000000000000000000000000000000000000000000000094254432d5553442d390000000000000000000000000000000000000000000000";
        when(configurationService.getResultBaseDir()).thenReturn(IEXEC_WORKER_TMP_FOLDER);
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo(expected);
    }

    @Test
    public void shouldNotGetCallbackDataSinceNotHexa(){
        String chainTaskId = "fake";
        when(configurationService.getResultBaseDir()).thenReturn(IEXEC_WORKER_TMP_FOLDER);
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }

    @Test
    public void shouldNotGetCallbackDataSinceNoFile(){
        String chainTaskId = "fake2";
        when(configurationService.getResultBaseDir()).thenReturn(IEXEC_WORKER_TMP_FOLDER);
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }

    @Test
    public void shouldNotGetCallbackDataSinceChainTaskIdMissing(){
        String chainTaskId = "";
        when(configurationService.getResultBaseDir()).thenReturn(IEXEC_WORKER_TMP_FOLDER);
        String callbackDataString = resultService.getCallbackDataFromFile(chainTaskId);
        assertThat(callbackDataString).isEqualTo("");
    }


}
