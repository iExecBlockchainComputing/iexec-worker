package com.iexec.worker.result;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ResultServiceTests {

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

}
