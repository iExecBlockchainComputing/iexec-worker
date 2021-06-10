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

package com.iexec.worker.docker;

import com.iexec.common.docker.DockerLogs;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.worker.config.DockerRegistryConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


public class DockerServiceTests {

    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    private WorkerConfigurationService workerConfigService = mock(WorkerConfigurationService.class);
    private DockerRegistryConfiguration dockerRegistryConfiguration = mock(DockerRegistryConfiguration.class);

    @Spy
    private DockerService dockerService = new DockerService(workerConfigService, dockerRegistryConfiguration);

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(false);
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
    }

    @Test
    public void shouldGetAuthForRegistry() {
        String registryName = "registryName";
        String registryUsername = "registryUsername";
        String registryPassword = "registryPassword";
        when(dockerRegistryConfiguration.getRegistries())
                .thenReturn(Collections.singletonList(DockerRegistryConfiguration.RegistryAuth.builder()
                        .address(registryName)
                        .username(registryUsername)
                        .password(registryPassword)
                        .build()));
        Optional<DockerRegistryConfiguration.RegistryAuth> authForRegistry =
                dockerService.getAuthForRegistry(registryName);
        assertThat(authForRegistry.isPresent());
        assertThat(authForRegistry.get().getAddress()).isEqualTo(registryName);
        assertThat(authForRegistry.get().getUsername()).isEqualTo(registryUsername);
        assertThat(authForRegistry.get().getPassword()).isEqualTo(registryPassword);
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceUnknownRegistry() {
        String registryName = "registryName";
        String registryUsername = "registryUsername";
        String registryPassword = "registryPassword";
        when(dockerRegistryConfiguration.getRegistries())
                .thenReturn(Collections.singletonList(DockerRegistryConfiguration.RegistryAuth.builder()
                        .address(registryName)
                        .username(registryUsername)
                        .password(registryPassword)
                        .build()));
        Optional<DockerRegistryConfiguration.RegistryAuth> authForRegistry =
                dockerService.getAuthForRegistry("unknownRegistryName");
        assertThat(authForRegistry.isEmpty());
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceMissingUsernameInConfig() {
        String registryName = "registryName";
        when(dockerRegistryConfiguration.getRegistries())
                .thenReturn(Collections.singletonList(DockerRegistryConfiguration.RegistryAuth.builder()
                        .address(registryName)
                        .username("")
                        .password("registryPassword")
                        .build()));
        Optional<DockerRegistryConfiguration.RegistryAuth> authForRegistry =
                dockerService.getAuthForRegistry(registryName);
        assertThat(authForRegistry.isEmpty());
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceMissingPasswordInConfig() {
        String registryName = "registryName";
        when(dockerRegistryConfiguration.getRegistries())
                .thenReturn(Collections.singletonList(DockerRegistryConfiguration.RegistryAuth.builder()
                        .address(registryName)
                        .username("registryUsername")
                        .password("")
                        .build()));
        Optional<DockerRegistryConfiguration.RegistryAuth> authForRegistry =
                dockerService.getAuthForRegistry(registryName);
        assertThat(authForRegistry.isEmpty());
    }

    @Test
    public void shouldRecordContainerThenRunThenRemoveContainerRecord() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        DockerRunResponse successResponse = DockerRunResponse.builder()
                .isSuccessful(true)
                .dockerLogs(DockerLogs.builder()
                        .stdout("stdout")
                        .stderr("stderr")
                        .build())
                .build();
        when(dockerClientInstanceMock.run(dockerRunRequest))
                .thenReturn(successResponse);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.isSuccessful()).isTrue();
        assertThat(dockerRunResponse.getStdout()).isEqualTo("stdout");
        assertThat(dockerRunResponse.getStderr()).isEqualTo("stderr");
        verify(dockerService).addToRunningContainersRecord(containerName);
        verify(dockerClientInstanceMock).run(dockerRunRequest);
        verify(dockerService).removeFromRunningContainersRecord(containerName);
    }

    @Test
    public void shouldNotRunSinceCannotAddContainerToRecords() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        doReturn(false).when(dockerService).addToRunningContainersRecord(containerName);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.isSuccessful()).isFalse();
        verify(dockerService).addToRunningContainersRecord(containerName);
        verify(dockerClientInstanceMock, never()).run(dockerRunRequest);
        verify(dockerService, never()).removeFromRunningContainersRecord(containerName);
    }

    @Test
    public void shouldRunThenRemoveContainerFromRecordsSinceRunFailed() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        DockerRunResponse failureResponse = DockerRunResponse.builder()
                .isSuccessful(false)
                .dockerLogs(DockerLogs.builder()
                        .stdout("stdout")
                        .stderr("stderr")
                        .build())
                .build();
        when(dockerClientInstanceMock.run(dockerRunRequest))
                .thenReturn(failureResponse);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.isSuccessful()).isFalse();
        assertThat(dockerRunResponse.getStdout()).isEqualTo("stdout");
        assertThat(dockerRunResponse.getStderr()).isEqualTo("stderr");
        verify(dockerService).addToRunningContainersRecord(containerName);
        verify(dockerClientInstanceMock).run(dockerRunRequest);
        verify(dockerService).removeFromRunningContainersRecord(containerName);
    }

    @Test
    public void shouldRunAndNotRemoveContainerFromRecordsSinceInDetachedMode() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(0) // in detached mode
                .build();
        DockerRunResponse successResponse = DockerRunResponse.builder()
                .isSuccessful(true)
                .build();
        when(dockerClientInstanceMock.run(dockerRunRequest))
                .thenReturn(successResponse);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.isSuccessful()).isTrue();
        verify(dockerService).addToRunningContainersRecord(containerName);
        verify(dockerClientInstanceMock).run(dockerRunRequest);
        verify(dockerService, never()).removeFromRunningContainersRecord(containerName);
    }

    // addToRunningContainersRecord

    @Test
    public void shouldAddToRunningContainersRecord() {
        String containerName = "containerName";
        Assertions.assertThat(dockerService
                .addToRunningContainersRecord(containerName)).isTrue();
    }

    @Test
    public void shouldNotAddToRunningContainersRecord() {
        String containerName = "containerName";
        dockerService.addToRunningContainersRecord(containerName);
        //add already existing name
        Assertions.assertThat(dockerService
                .addToRunningContainersRecord(containerName)).isFalse();
    }

    // removeFromRunningContainersRecord

    @Test
    public void shouldNotRemoveFromRunningContainersRecord() {
        String containerName = "containerName";

        Assertions.assertThat(dockerService
                .removeFromRunningContainersRecord(containerName)).isFalse();
    }

    // stopRunningContainers

    @Test
    public void shouldStopRunningContainers() {
        String container1 = "container1";
        String container2 = "container2";
        dockerService.addToRunningContainersRecord(container1);
        dockerService.addToRunningContainersRecord(container2);
        
        when(dockerClientInstanceMock.stopContainer(container1)).thenReturn(true);
        when(dockerClientInstanceMock.stopContainer(container2)).thenReturn(true);

        dockerService.stopRunningContainers();
        verify(dockerClientInstanceMock, times(1)).stopContainer(container1);
        verify(dockerClientInstanceMock, times(1)).stopContainer(container2);
    }

    @Test
    public void shouldNotStopRunningContainers() {
        // no running container
        dockerService.stopRunningContainers();
        verify(dockerClientInstanceMock, never()).stopContainer(anyString());
    }
}
