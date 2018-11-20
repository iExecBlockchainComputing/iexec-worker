package com.iexec.worker.chain;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.CustomDockerClient;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class ContributionServiceTests {

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private ContributionService contributionService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldContributionAuthorizationBeValid() {
        // PRIVATE_KEY_STRING: "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
        // PUBLIC_KEY_STRING: "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";
        String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskid = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        ContributionAuthorization contribAuth = ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskid)
                .enclave(enclaveWallet)
                .signR(BytesUtils.stringToBytes("0x99f6b19da6aeb2133763a11204b9895c5b7d0478d08ae3d889a6bd6c820b612f"))
                .signS(BytesUtils.stringToBytes("0x0b64b1f9ceb8472f4944da55d3b75947a04618bae5ddd57a7a2a2d14c3802b7e"))
                .signV((byte) 27)
                .build();

        assertThat(contributionService.isContributionAuthorizationValid(contribAuth, signingAddress)).isTrue();
    }
}
