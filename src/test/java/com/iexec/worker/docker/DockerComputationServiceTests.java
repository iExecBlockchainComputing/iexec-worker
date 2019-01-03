package com.iexec.worker.docker;

import com.iexec.common.security.Signature;
import com.iexec.worker.result.ResultService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigInteger;

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
    public void shoudlGetVrsOfSignatureFile() throws IOException {
        int vExpected = 27;
        String rExpected = "4e3db90707b569fbbbf9dc00a3b473e59a976fd2acff1fc022cf703f172d07ec";
        String sExpected = "6777dcd28b761aebf533188770ef304e67472297ced7d99b0745131662b9f597";

        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources");
        Signature signature = dockerComputationService.getExecutionEnclaveSignature("chainTaskId");

        assertThat(signature.getSignV()).isEqualTo(new BigInteger("" + vExpected).toByteArray()[0]);
        assertThat(cleanHexPrefix(bytesToString(signature.getSignR()))).isEqualTo(rExpected);
        assertThat(cleanHexPrefix(bytesToString(signature.getSignS()))).isEqualTo(sExpected);
    }

    @Test
    public void shoudlNotReturnSignatureWhenFileMissing() throws IOException {
        when(resultService.getResultFolderPath("chainTaskId")).thenReturn("./src/test/resources/fakefolder");
        Signature signature = dockerComputationService.getExecutionEnclaveSignature("chainTaskId");

        assertThat(signature).isNull();
    }

}
