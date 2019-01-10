package com.iexec.worker.docker;

import com.iexec.common.security.Signature;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.security.TeeSignature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import static com.iexec.common.utils.BytesUtils.bytesToString;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.web3j.utils.Numeric.cleanHexPrefix;

public class DockerComputationServiceTests {

    @Mock
    private ResultService resultService;

    @InjectMocks
    private DockerComputationService dockerComputationService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetEnclaveSignature() throws IOException {
        int vExpected = 27;
        String rExpected = "0x253554311f2793b72785a45eff4cbbe04c45d2e5a4d89057dfbc721e69b61d39";
        String sExpected = "0x6bdf554c8c12c158d12f08299afbe0d9c8533bf420a5d3f63ed9827047eab8d";

        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources");
        Optional<TeeSignature.Sign> enclaveSignature = dockerComputationService.getEnclaveSignature("chainTaskId");

        assertThat(enclaveSignature.isPresent()).isTrue();
        assertThat(enclaveSignature.get().getV()).isEqualTo(vExpected);
        assertThat(enclaveSignature.get().getR()).isEqualTo(rExpected);
        assertThat(enclaveSignature.get().getS()).isEqualTo(sExpected);
    }

    @Test
    public void shouldNotGetEnclaveSignatureSinceFileMissing() throws IOException {
        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources/fakefolder");
        Optional<TeeSignature.Sign> enclaveSignature = dockerComputationService.getEnclaveSignature("chainTaskId");

        assertThat(enclaveSignature.isPresent()).isFalse();
    }

}
