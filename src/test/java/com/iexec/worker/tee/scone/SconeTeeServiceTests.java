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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SconeTeeServiceTests {

    private static final String REGISTRY_NAME = "registryName";
    private static final String IMAGE_URI = REGISTRY_NAME +"/some/image/name:x.y";
    private static final String SESSION_ID = "sessionId";
    private static final String CAS_URL = "casUrl";
    private static final String LAS_URL = "lasUrl";
    private static final boolean SHOW_VERSION = true;
    private static final String LOG_LEVEL = "debug";
    public static final String REGISTRY_USERNAME = "registryUsername";
    public static final String REGISTRY_PASSWORD = "registryPassword";
    public static final long heapSize = 1024;

    @Captor
    ArgumentCaptor<DockerRunRequest> dockerRunRequestArgumentCaptor;
    @InjectMocks
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
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

    @BeforeEach
    void init() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(sconeConfig.getRegistryName()).thenReturn(REGISTRY_NAME);
        when(sconeConfig.getRegistryUsername()).thenReturn(REGISTRY_USERNAME);
        when(sconeConfig.getRegistryPassword()).thenReturn(REGISTRY_PASSWORD);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(dockerService.getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(dockerClientInstanceMock);
    }

    @Test
    void shouldStartLasService() {
        when(sconeConfig.getLasContainerName()).thenReturn("containerName");
        when(sconeConfig.getLasImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().isSuccessful(true).build());

        Assertions.assertThat(teeSconeService.startLasService()).isTrue();
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
    void shouldNotStartLasServiceSinceUnknownRegistry() {
        when(sconeConfig.getLasImageUri()).thenReturn(IMAGE_URI);
        when(sconeConfig.getRegistryName()).thenReturn("unknownRegistry");

        Exception exception = assertThrows(
                RuntimeException.class,
                () -> teeSconeService.startLasService()
        );

        Assertions.assertThat(exception.getMessage().contains("not from a known registry"))
                .isTrue();
    }

    @Test
    void shouldNotStartLasServiceSinceClientError() throws Exception {
        when(sconeConfig.getLasImageUri()).thenReturn(IMAGE_URI);
        when(dockerService.getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(null);

        Assertions.assertThat(teeSconeService.startLasService()).isFalse();
    }

    @Test
    void shouldNotStartLasServiceSinceCannotPullImage() {
        when(sconeConfig.getLasContainerName()).thenReturn("containerName");
        when(sconeConfig.getLasImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(false);

        Assertions.assertThat(teeSconeService.startLasService()).isFalse();
    }

    @Test
    void shouldNotStartLasServiceSinceCannotRunDockerContainer() {
        when(sconeConfig.getLasContainerName()).thenReturn("containerName");
        when(sconeConfig.getLasImageUri()).thenReturn(IMAGE_URI);
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().isSuccessful(false).build());

        Assertions.assertThat(teeSconeService.startLasService()).isFalse();
    }

    @Test
    void shouldBuildPreComputeDockerEnv() {
        when(sconeConfig.getLasUrl()).thenReturn(LAS_URL);
        when(sconeConfig.getCasUrl()).thenReturn(CAS_URL);
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        long preComputeHeapSize = 1024;
        Assertions.assertThat(teeSconeService.buildPreComputeDockerEnv(SESSION_ID,
                preComputeHeapSize))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/pre-compute",
                    "SCONE_HEAP=" + 1024,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

    @Test
    void shouldBuildComputeDockerEnv() {
        when(sconeConfig.getLasUrl()).thenReturn(LAS_URL);
        when(sconeConfig.getCasUrl()).thenReturn(CAS_URL);
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        Assertions.assertThat(teeSconeService.buildComputeDockerEnv(SESSION_ID, heapSize))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/app",
                    "SCONE_HEAP=" + 1024,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

    @Test
    void shouldBuildPostComputeDockerEnv() {
        when(sconeConfig.getLasUrl()).thenReturn(LAS_URL);
        when(sconeConfig.getCasUrl()).thenReturn(CAS_URL);
        when(sconeConfig.getLogLevel()).thenReturn(LOG_LEVEL);
        when(sconeConfig.isShowVersion()).thenReturn(SHOW_VERSION);

        Assertions.assertThat(teeSconeService.getPostComputeDockerEnv(SESSION_ID, 1024))
                .isEqualTo(List.of(
                    "SCONE_CAS_ADDR=" + CAS_URL,
                    "SCONE_LAS_ADDR=" + LAS_URL,
                    "SCONE_CONFIG_ID=" + SESSION_ID + "/post-compute",
                    "SCONE_HEAP=" + 1024,
                    "SCONE_LOG=" + LOG_LEVEL,
                    "SCONE_VERSION=" + 1));
    }

}