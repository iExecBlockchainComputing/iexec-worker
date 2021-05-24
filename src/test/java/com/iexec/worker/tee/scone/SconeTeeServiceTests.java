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
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
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

    private static final String IMAGE_URI = "IMAGE_URI";
    private static final String SESSION_ID = "sessionId";
    private static final String CAS_URL = "casUrl";
    private static final String LAS_URL = "lasUrl";
    public static final String REGISTRY_USERNAME = "registryUsername";
    public static final String REGISTRY_PASSWORD = "registryPassword";
    public static final long heapSize = 1024;

    @Captor
    ArgumentCaptor<DockerRunRequest> dockerRunRequestArgumentCaptor;
    @InjectMocks
    private SconeTeeService sconeTeeService;
    @Mock
    private SconeLasConfiguration sconeLasConfig;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerService dockerService;
    @Mock
    PublicConfigurationService publicConfigService;
    @Mock
    private SgxService sgxService;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        when(sconeLasConfig.getRegistryUsername()).thenReturn(REGISTRY_USERNAME);
        when(sconeLasConfig.getRegistryPassword()).thenReturn(REGISTRY_PASSWORD);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(dockerService.getClient(REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(dockerClientInstanceMock);
    }

    @Test
    public void shouldStartLasService() {
        when(sconeLasConfig.getImageUri()).thenReturn(IMAGE_URI);
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
    public void shouldNotStartLasServiceSinceClientError() {
        when(dockerService.getClient(REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(null);

        Assertions.assertThat(sconeTeeService.startLasService()).isFalse();
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
    public void shouldBuildPreComputeDockerEnv() {
        when(sconeLasConfig.getUrl()).thenReturn(LAS_URL);
        when(sconeLasConfig.getSconeCasUrl()).thenReturn(CAS_URL);

        String preComputeHeapSize = "preComputeHeapSize";
        Assertions.assertThat(sconeTeeService.buildPreComputeDockerEnv(SESSION_ID,
                preComputeHeapSize))
                .isEqualTo(SconeConfig.builder()
                        .sconeLasAddress(LAS_URL)
                        .sconeCasAddress(CAS_URL)
                        .sconeConfigId(SESSION_ID + "/pre-compute")
                        .sconeHeap(preComputeHeapSize)
                        .build().toDockerEnv());
    }

    @Test
    public void shouldBuildComputeDockerEnv() {
        when(sconeLasConfig.getUrl()).thenReturn(LAS_URL);
        when(sconeLasConfig.getSconeCasUrl()).thenReturn(CAS_URL);

        Assertions.assertThat(sconeTeeService.buildComputeDockerEnv(SESSION_ID, heapSize))
                .isEqualTo(SconeConfig.builder()
                        .sconeLasAddress(LAS_URL)
                        .sconeCasAddress(CAS_URL)
                        .sconeConfigId(SESSION_ID + "/app")
                        .sconeHeap(String.valueOf(1024))
                        .build().toDockerEnv());
    }

    @Test
    public void shouldBuildPostComputeDockerEnv() {
        when(sconeLasConfig.getUrl()).thenReturn(LAS_URL);
        when(sconeLasConfig.getSconeCasUrl()).thenReturn(CAS_URL);

        Assertions.assertThat(sconeTeeService.getPostComputeDockerEnv(SESSION_ID))
                .isEqualTo(SconeConfig.builder()
                        .sconeLasAddress(LAS_URL)
                        .sconeCasAddress(CAS_URL)
                        .sconeConfigId(SESSION_ID + "/post-compute")
                        .sconeHeap("4G")
                        .build().toDockerEnv());
    }

}