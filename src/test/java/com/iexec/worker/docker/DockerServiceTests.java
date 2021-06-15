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

import com.github.dockerjava.api.exception.DockerException;
import com.iexec.common.docker.DockerLogs;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Optional;

import static com.iexec.common.docker.client.DockerClientInstance.DEFAULT_DOCKER_REGISTRY;
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
    }

    /**
     * getClient()
     */

    @Test
    public void shouldGetUnauthenticatedClient() {
        DockerClientInstance dockerClientInstance = dockerService.getClient();
        assertThat(dockerClientInstance.getClient().authConfig().getPassword()).isNull();
    }

    /**
     * getClient(imageName)
     */

    // docker.io/image:tag
    @Test
    public void shouldGetAuthenticatedClientWithDockerIoRegistry() throws Exception {
        String registry = DEFAULT_DOCKER_REGISTRY;
        String imageName = registry + "/name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .address(registry)
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.of(credentials));
        doReturn(dockerClientInstanceMock)
                .when(dockerService)
                .getClient(registry, credentials.getUsername(), credentials.getPassword());
        dockerService.getClient(imageName);
        verify(dockerService).getClient(registry, credentials.getUsername(), credentials.getPassword());
        verify(dockerService, never()).getClient();
    }

    // registry.xyz/name:tag
    @Test
    public void shouldGetAuthenticatedClientWithCustomRegistry() throws Exception {
        String registry = "registry.xyz";
        String imageName = registry + "/name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .address(registry)
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.of(credentials));
        doReturn(dockerClientInstanceMock)
                .when(dockerService)
                .getClient(registry, credentials.getUsername(), credentials.getPassword());
        dockerService.getClient(imageName);
        verify(dockerService).getClient(registry, credentials.getUsername(), credentials.getPassword());
        verify(dockerService, never()).getClient();
    }

    // registry:port/image:tag
    @Test
    public void shouldGetAuthenticatedClientWithCustomRegistryAndPort() throws Exception {
        String registry = "registry.host.com:5050";
        String imageName = registry + "/name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .address(registry)
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.of(credentials));
        doReturn(dockerClientInstanceMock)
                .when(dockerService)
                .getClient(registry, credentials.getUsername(), credentials.getPassword());
        dockerService.getClient(imageName);
        verify(dockerService).getClient(registry, credentials.getUsername(), credentials.getPassword());
        verify(dockerService, never()).getClient();
    }
    
    // image:tag
    @Test
    public void shouldGetAuthenticatedClientWithDefaultRegistryWhenRegistryNotInImageName() throws Exception {
        String registry = "";
        String imageName = registry + "name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .address(registry)
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(DEFAULT_DOCKER_REGISTRY))
                .thenReturn(Optional.of(credentials));
        doReturn(dockerClientInstanceMock)
                .when(dockerService)
                .getClient(
                        DEFAULT_DOCKER_REGISTRY,
                        credentials.getUsername(),
                        credentials.getPassword());
        dockerService.getClient(imageName);
        verify(dockerService).getClient(
                        DEFAULT_DOCKER_REGISTRY,
                        credentials.getUsername(),
                        credentials.getPassword());
        verify(dockerService, never()).getClient();
    }

    @Test
    public void shouldGetUnauthenticatedClientWhenCredentialsNotFoundWithCustomRegistry() throws Exception {
        String registry = "registry.xyz";
        String imageName = registry + "/name:tag";
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.empty());
        DockerClientInstance instance = dockerService.getClient(imageName);
        assertThat(instance.getClient().authConfig().getRegistryAddress()).isEqualTo(registry);
        assertThat(instance.getClient().authConfig().getPassword()).isNull();
        verify(dockerService, never()).getClient(anyString(), anyString(), anyString());
    }

    @Test
    public void shouldGetUnauthenticatedClientWhenAuthFailureWithCustomRegistry() throws Exception {
        String registry = "registry.xyz";
        String imageName = registry + "/name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.of(credentials));
        doThrow(DockerException.class)
                .when(dockerService)
                .getClient(registry, credentials.getUsername(), credentials.getPassword());
        DockerClientInstance instance = dockerService.getClient(imageName);
        assertThat(instance.getClient().authConfig().getRegistryAddress()).isEqualTo(registry);
        verify(dockerService).getClient(registry, credentials.getUsername(), credentials.getPassword());
    }

    /**
     * run()
     */

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
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
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
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
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
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.run(dockerRunRequest))
                .thenReturn(successResponse);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(dockerRunResponse.isSuccessful()).isTrue();
        verify(dockerService).addToRunningContainersRecord(containerName);
        verify(dockerClientInstanceMock).run(dockerRunRequest);
        verify(dockerService, never()).removeFromRunningContainersRecord(containerName);
    }

    /**
     * addToRunningContainersRecord()
     */

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

    /**
     * removeFromRunningContainersRecord()
     */

    @Test
    public void shouldNotRemoveFromRunningContainersRecord() {
        String containerName = "containerName";

        Assertions.assertThat(dockerService
                .removeFromRunningContainersRecord(containerName)).isFalse();
    }

    /**
     * stopRunningContainers
     */

    @Test
    public void shouldStopRunningContainers() {
        String container1 = "container1";
        String container2 = "container2";
        dockerService.addToRunningContainersRecord(container1);
        dockerService.addToRunningContainersRecord(container2);

        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.stopContainer(container1)).thenReturn(true);
        when(dockerClientInstanceMock.stopContainer(container2)).thenReturn(true);

        dockerService.stopRunningContainers();
        verify(dockerClientInstanceMock, times(1)).stopContainer(container1);
        verify(dockerClientInstanceMock, times(1)).stopContainer(container2);
    }

    @Test
    public void shouldNotStopRunningContainers() {
        // no running container
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        dockerService.stopRunningContainers();
        verify(dockerClientInstanceMock, never()).stopContainer(anyString());
    }
}
