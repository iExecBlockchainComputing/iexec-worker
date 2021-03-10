/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.tee.scone;

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SconeTeeServiceTests {

    private final static String IMAGE_URI = "IMAGE_URI";
    @Captor
    ArgumentCaptor<DockerRunRequest> dockerRunRequestArgumentCaptor;
    @InjectMocks
    private SconeTeeService sconeTeeService;
    @Mock
    private SgxService sgxService;
    @Mock
    private SconeLasConfiguration sconeLasConfig;
    @Mock
    private DockerService dockerService;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
    }

    @Test
    public void shouldStartLasService() {
        when(sconeLasConfig.getContainerName()).thenReturn("containerName");
        when(sconeLasConfig.getImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().isSuccessful(true).build());

        Assertions.assertThat(sconeTeeService.startLasService()).isTrue();
        verify(dockerService).run(dockerRunRequestArgumentCaptor.capture());
        DockerRunRequest dockerRunRequest = dockerRunRequestArgumentCaptor.getValue();
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .containerName("containerName")
                        .imageUri(IMAGE_URI)
                        .isSgx(true)
                        .maxExecutionTime(0)
                        .build()
        );
    }

    @Test
    public void shouldNotStartLasServiceSinceCannotPullImage() {
        when(sconeLasConfig.getContainerName()).thenReturn("containerName");
        when(sconeLasConfig.getImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(false);

        Assertions.assertThat(sconeTeeService.startLasService()).isFalse();
    }

    @Test
    public void shouldNotStartLasServiceSinceCannotRunDockerContainer() {
        when(sconeLasConfig.getContainerName()).thenReturn("containerName");
        when(sconeLasConfig.getImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().isSuccessful(false).build());

        Assertions.assertThat(sconeTeeService.startLasService()).isFalse();
    }

    @Test
    public void buildSconeDockerEnv() {
        String sconeConfigId = "sconeConfigId";
        String sconeCasUrl = "sconeCasUrl";
        String sconeHeap = "sconeHeap";
        String url = "url";
        when(sconeLasConfig.getUrl()).thenReturn(url);

        Assertions.assertThat(
                sconeTeeService.buildSconeDockerEnv(sconeConfigId,
                        sconeCasUrl,
                        sconeHeap)).isEqualTo(
                SconeConfig.builder()
                        .sconeLasAddress(url)
                        .sconeCasAddress(sconeCasUrl)
                        .sconeConfigId(sconeConfigId)
                        .sconeHeap(sconeHeap)
                        .build().toDockerEnv()
        );
    }

}