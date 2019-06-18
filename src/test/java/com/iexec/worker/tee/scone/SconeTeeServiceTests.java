package com.iexec.worker.tee.scone;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;

import static com.iexec.worker.chain.ContributionService.computeResultHash;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;
import static com.iexec.worker.chain.ContributionService.computeResultSeal;


public class SconeTeeServiceTests {

    // @Mock
    // private IexecHubService iexecHubService;

    @InjectMocks
    private SconeTeeService sconeTeeService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void ShouldReturnTrueSinceIsEnclaveSignatureValid() {
        String chainTaskId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";
        String worker = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        String deterministHash = "0xb7e58c9d6fbde4420e87af44786ec46c797123d0667b72920b4cead23d60188b";

        String resultHash = computeResultHash(chainTaskId, deterministHash);
        String resultSeal = computeResultSeal(worker, chainTaskId, deterministHash);

        String enclaveAddress = "0x3cB738D98D7A70e81e81B0811Fae2452BcA049Bc";

        String r = "0xfe0d8948ca8739b0926ed5729532686b283755a1c1e660abf1ebd6362d1545c8";
        String s = "0x14e53d7cd66ec0a1cfe330b1e16e460ae354d33fb84cf9d62213b10c109f0db5";
        int v = 27;

        Signature enclaveSignature = new Signature(BytesUtils.stringToBytes(r), BytesUtils.stringToBytes(s), (byte) v);

        assertThat(sconeTeeService.isEnclaveSignatureValid(resultHash, resultSeal, enclaveSignature, enclaveAddress)).isTrue();
    }

    @Test
    public void shouldGetEnclaveSignature() throws IOException {
        String chainTaskId = "1234";
        String rExpected = "0x253554311f2793b72785a45eff4cbbe04c45d2e5a4d89057dfbc721e69b61d39";
        String sExpected = "0x6bdf554c8c12c158d12f08299afbe0d9c8533bf420a5d3f63ed9827047eab8d1";
        byte vExpected = 27;

        when(configurationService.getTaskOutputDir(chainTaskId))
                .thenReturn("./src/test/resources/tmp/test-worker/" + chainTaskId + "/output");
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