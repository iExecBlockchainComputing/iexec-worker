package com.iexec.worker.result;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.security.TeeSignature;
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
        int vExpected = 27;
        String rExpected = "0x253554311f2793b72785a45eff4cbbe04c45d2e5a4d89057dfbc721e69b61d39";
        String sExpected = "0x6bdf554c8c12c158d12f08299afbe0d9c8533bf420a5d3f63ed9827047eab8d";

        when(configurationService.getResultBaseDir()).thenReturn("./src/test/resources/tmp/test-worker");
        Optional<TeeSignature.Sign> enclaveSignature = resultService.getEnclaveSignatureFromFile(chainTaskId);

        assertThat(enclaveSignature.isPresent()).isTrue();
        assertThat(enclaveSignature.get().getV()).isEqualTo(vExpected);
        assertThat(enclaveSignature.get().getR()).isEqualTo(rExpected);
        assertThat(enclaveSignature.get().getS()).isEqualTo(sExpected);
    }

    @Test
    public void shouldNotGetEnclaveSignatureSinceFileMissing() throws IOException {
        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources/fakefolder");
        Optional<TeeSignature.Sign> enclaveSignature = resultService.getEnclaveSignatureFromFile("chainTaskId");

        assertThat(enclaveSignature.isPresent()).isFalse();
    }

}
