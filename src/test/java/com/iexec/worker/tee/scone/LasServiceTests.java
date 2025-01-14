/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LasServiceTests {
    private static final String CONTAINER_NAME = "iexec-las";
    private static final String REGISTRY_NAME = "registryName";
    private static final String IMAGE_URI = REGISTRY_NAME + "/some/image/name:x.y";
    private static final String REGISTRY_USERNAME = "registryUsername";
    private static final String REGISTRY_PASSWORD = "registryPassword";

    @Captor
    ArgumentCaptor<DockerRunRequest> dockerRunRequestArgumentCaptor;

    @Mock
    SconeConfiguration sconeConfiguration;
    @Mock
    WorkerConfigurationService workerConfigService;
    @Mock
    DockerService dockerService;
    @Mock
    SgxService sgxService;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    private LasService lasService;

    @BeforeEach
    void init() {
        lasService = spy(new LasService(
                CONTAINER_NAME,
                IMAGE_URI,
                sconeConfiguration,
                workerConfigService,
                sgxService,
                dockerService
        ));
    }

    private void createLasServiceStubs() {
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.NATIVE);
        when(sconeConfiguration.getRegistry())
                .thenReturn(new SconeConfiguration.SconeRegistry(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD));
        when(dockerService.getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(dockerClientInstanceMock);
    }

    // region start
    @Test
    void shouldStartLasService() {
        createLasServiceStubs();
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().finalStatus(DockerRunFinalStatus.SUCCESS).build());
        List<Device> devices = Arrays.stream(SgxDriverMode.NATIVE.getDevices()).map(Device::parse).toList();
        when(sgxService.getSgxDevices()).thenReturn(devices);

        assertTrue(lasService.start());
        verify(dockerService).run(dockerRunRequestArgumentCaptor.capture());
        DockerRunRequest dockerRunRequest = dockerRunRequestArgumentCaptor.getValue();
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .hostConfig(HostConfig.newHostConfig().withDevices(devices))
                        .containerName(CONTAINER_NAME)
                        .imageUri(IMAGE_URI)
                        .sgxDriverMode(SgxDriverMode.NATIVE)
                        .maxExecutionTime(0)
                        .build()
        );
        assertTrue(lasService.isStarted());
    }

    @Test
    void shouldStartLasServiceOnlyOnce() {
        createLasServiceStubs();
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().finalStatus(DockerRunFinalStatus.SUCCESS).build());

        assertTrue(lasService.start());
        assertTrue(lasService.start());
        assertTrue(lasService.isStarted());
        verify(dockerService).getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD);
        verify(dockerService).run(any());
    }

    @Test
    void shouldNotStartLasServiceSinceUnknownRegistry() {
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.NATIVE);
        when(sconeConfiguration.getRegistry())
                .thenReturn(new SconeConfiguration.SconeRegistry(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD));

        final LasService corruptLasService = new LasService(
                CONTAINER_NAME,
                "unknownRegistry",
                sconeConfiguration,
                workerConfigService,
                sgxService,
                dockerService
        );

        assertFalse(corruptLasService.start());
        assertFalse(corruptLasService.isStarted());
    }

    @Test
    void shouldNotStartLasServiceSinceClientError() {
        createLasServiceStubs();
        when(dockerService.getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenReturn(null);

        assertFalse(lasService.start());
        assertFalse(lasService.isStarted());
    }

    @Test
    void shouldNotStartLasServiceSinceClientException() {
        createLasServiceStubs();
        // getClient calls DockerClientFactory.getDockerClientInstance which can throw runtime exceptions
        when(dockerService.getClient(REGISTRY_NAME, REGISTRY_USERNAME, REGISTRY_PASSWORD))
                .thenThrow(RuntimeException.class);

        assertFalse(lasService.start());
        assertFalse(lasService.isStarted());
    }

    @Test
    void shouldNotStartLasServiceSinceCannotPullImage() {
        createLasServiceStubs();
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(false);

        assertFalse(lasService.start());
        assertFalse(lasService.isStarted());
    }

    @Test
    void shouldNotStartLasServiceSinceCannotRunDockerContainer() {
        createLasServiceStubs();
        when(dockerClientInstanceMock.pullImage(IMAGE_URI)).thenReturn(true);
        when(dockerService.run(any()))
                .thenReturn(DockerRunResponse.builder().finalStatus(DockerRunFinalStatus.FAILED).build());

        assertFalse(lasService.start());
        assertFalse(lasService.isStarted());
    }
    // endregion

    // region stopAndRemoveContainer
    @Test
    void shouldStopAndRemoveContainer() {
        when(lasService.isStarted()).thenReturn(true).thenCallRealMethod();
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(dockerClientInstanceMock.stopAndRemoveContainer(CONTAINER_NAME)).thenReturn(false);

        assertTrue(lasService.stopAndRemoveContainer());
        assertFalse(lasService.isStarted());

        verify(dockerClientInstanceMock).stopAndRemoveContainer(CONTAINER_NAME);
    }

    @Test
    void shouldNotStopSinceNotStarted() {
        when(lasService.isStarted()).thenReturn(false);

        assertTrue(lasService.stopAndRemoveContainer());
        assertFalse(lasService.isStarted());

        verifyNoInteractions(dockerClientInstanceMock);
    }

    @Test
    void shouldFailTotStopAndRemoveContainer() {
        when(lasService.isStarted()).thenReturn(true).thenCallRealMethod();
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(dockerClientInstanceMock.stopAndRemoveContainer(CONTAINER_NAME)).thenReturn(true);

        assertFalse(lasService.stopAndRemoveContainer());
        assertTrue(lasService.isStarted());

        verify(dockerClientInstanceMock).stopAndRemoveContainer(CONTAINER_NAME);
    }
    // endregion
}
