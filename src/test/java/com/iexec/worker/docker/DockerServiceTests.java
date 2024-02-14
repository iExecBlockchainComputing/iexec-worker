/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.containers.DockerLogs;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.client.DockerClientFactory;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.iexec.commons.containers.client.DockerClientInstance.DEFAULT_DOCKER_REGISTRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DockerServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    private final WorkerConfigurationService workerConfigService = mock(WorkerConfigurationService.class);
    private final DockerRegistryConfiguration dockerRegistryConfiguration = mock(DockerRegistryConfiguration.class);

    @Spy
    private DockerService dockerService = new DockerService(workerConfigService, dockerRegistryConfiguration);

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    //region getClient
    @Test
    void shouldGetUnauthenticatedClient() {
        DockerClientInstance dockerClientInstance = dockerService.getClient();
        assertThat(dockerClientInstance).isEqualTo(DockerClientFactory.getDockerClientInstance());
    }

    private static Stream<Arguments> provideBadParametersForGetClient() {
        return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of("", "", ""),
                Arguments.of("a", "b", null),
                Arguments.of("a", "b", ""),
                Arguments.of("a", null, "c"),
                Arguments.of("a", "", "c"),
                Arguments.of(null, "b", "c"),
                Arguments.of("", "b", "c")
        );
    }

    @ParameterizedTest
    @MethodSource("provideBadParametersForGetClient")
    void shouldFailToGetAuthenticatedClient(String registryAddress, String registryUsername, String registryPassword) {
        assertThatThrownBy(() -> dockerService.getClient(registryAddress, registryUsername, registryPassword))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All Docker registry parameters must be provided");
    }

    // docker.io/image:tag
    @Test
    void shouldGetAuthenticatedClientWithDockerIoRegistry() {
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
    void shouldGetAuthenticatedClientWithCustomRegistry() {
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
    void shouldGetAuthenticatedClientWithCustomRegistryAndPort() {
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
    void shouldGetAuthenticatedClientWithDefaultRegistryWhenRegistryNotInImageName() {
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
    void shouldGetUnauthenticatedClientWhenCredentialsNotFoundWithCustomRegistry() {
        String registry = "registry.xyz";
        String imageName = registry + "/name:tag";
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.empty());

        DockerClientInstance instance = dockerService.getClient(imageName);
        verify(dockerService, never()).getClient(anyString(), anyString(), anyString());
        assertThat(instance).isEqualTo(DockerClientFactory.getDockerClientInstance(registry));
    }

    @Test
    void shouldGetUnauthenticatedClientWhenAuthFailureWithCustomRegistry() {
        String registry = "registry.xyz";
        String imageName = registry + "/name:tag";
        RegistryCredentials credentials = RegistryCredentials.builder()
                .username("username")
                .password("password")
                .build();
        when(dockerRegistryConfiguration.getRegistryCredentials(registry))
                .thenReturn(Optional.of(credentials));
        // getClient calls DockerClientFactory.getDockerClientInstance which can throw runtime exceptions
        doThrow(RuntimeException.class)
                .when(dockerService)
                .getClient(registry, credentials.getUsername(), credentials.getPassword());

        DockerClientInstance instance = dockerService.getClient(imageName);
        verify(dockerService).getClient(registry, credentials.getUsername(), credentials.getPassword());
        assertThat(instance).isEqualTo(DockerClientFactory.getDockerClientInstance(registry));
    }
    //endregion

    //region run
    @Test
    void shouldRecordContainerThenRunThenRemoveContainerRecord() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        DockerRunResponse successResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.SUCCESS)
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
    void shouldNotRunSinceCannotAddContainerToRecords() {
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
    void shouldRunThenRemoveContainerFromRecordsSinceRunFailed() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        DockerRunResponse failureResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.FAILED)
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
    void shouldRunAndNotRemoveContainerFromRecordsSinceInDetachedMode() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(0) // in detached mode
                .build();
        DockerRunResponse successResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.SUCCESS)
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

    @Test
    void shouldNotDisplayLogsWhenChainTaskIdNotFound(CapturedOutput output) {
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName("containerName").build();
        DockerRunResponse successResponse = DockerRunResponse.builder().build();
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.run(dockerRunRequest)).thenReturn(successResponse);
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(true);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(output.getOut()).contains("Cannot print developer logs");
    }

    @Test
    void shouldDisplayLogs(CapturedOutput output) {
        final String stdoutMessage = "Message written on stdout";
        final String stderrMessage = "Message written on stderr";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .containerName("containerName").build();
        DockerLogs dockerLogs = DockerLogs.builder()
                .stdout(stdoutMessage)
                .stderr(stderrMessage)
                .build();
        DockerRunResponse responseWithLogs = DockerRunResponse.builder()
                .dockerLogs(dockerLogs)
                .build();
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.run(dockerRunRequest)).thenReturn(responseWithLogs);
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(true);
        when(workerConfigService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn("./src/test/resources/tmp/test-worker/" + CHAIN_TASK_ID + IexecFileHelper.SLASH_INPUT);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn("./src/test/resources/tmp/test-worker/" + CHAIN_TASK_ID
                        + IexecFileHelper.SLASH_OUTPUT + IexecFileHelper.SLASH_IEXEC_OUT);

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        assertThat(dockerRunResponse).isNotNull();
        assertThat(output.getOut()).contains(
                "Developer logs of docker run [chainTaskId:chainTaskId]",
                stdoutMessage,
                stderrMessage);
    }
    //endregion

    //#region getInputBind()

    @Test
    void shouldGetInputBind() {
        String taskInputDir = "/input/dir";
        when(workerConfigService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn(taskInputDir);
        assertThat(dockerService.getInputBind(CHAIN_TASK_ID))
                // "/input/dir:/iexec_in"
                .isEqualTo(taskInputDir + ":" + File.separator + "iexec_in");
    }

    //#endregion

    //#region getIexecOutBind()

    @Test
    void shouldGetIexecOutBind() {
        String taskIexecOutDir = "/iexec/out/dir";
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(taskIexecOutDir);
        assertThat(dockerService.getIexecOutBind(CHAIN_TASK_ID))
                // "/iexec/out/dir:/iexec_out"
                .isEqualTo(taskIexecOutDir + ":" + File.separator + "iexec_out");
    }

    //#endregion

    //#region stopAllRunningContainers

    @Test
    void shouldStopAllRunningContainers() {
        String container1 = "container1";
        String container2 = "container2";
        dockerService.addToRunningContainersRecord(container1);
        dockerService.addToRunningContainersRecord(container2);

        dockerService.stopAllRunningContainers();
        // Verify all containers are removed
        verify(dockerService, times(2)).stopRunningContainer(anyString());
        verify(dockerService).stopRunningContainer(container1);
        verify(dockerService).stopRunningContainer(container2);
    }

    @Test
    void shouldNotStopRunningContainers() {
        // no running container
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        dockerService.stopAllRunningContainers();
        verify(dockerClientInstanceMock, never()).stopContainer(anyString());
    }

    //#endregion

    //#region stopRunningContainersWithNamePattern()

    @Test
    void shouldStopRunningContainersWithNamePattern() {
        String computeContainerName = "awesome-app-" + CHAIN_TASK_ID;
        String preComputeContainerName = computeContainerName + "-tee-pre-compute";
        String postComputeContainerName = computeContainerName + "-tee-post-compute";
        // Add task related containers to record
        dockerService.addToRunningContainersRecord(preComputeContainerName);
        dockerService.addToRunningContainersRecord(computeContainerName);
        dockerService.addToRunningContainersRecord(postComputeContainerName);
        // Add some other tasks containers
        dockerService.addToRunningContainersRecord("containerName1");
        dockerService.addToRunningContainersRecord("containerName2");

        Predicate<String> containsChainTaskId = name -> name.contains(CHAIN_TASK_ID);
        dockerService.stopRunningContainersWithNamePredicate(containsChainTaskId);
        // Verify we removed all containers matching the predicate
        verify(dockerService, times(3)).stopRunningContainer(anyString());
        // Verify we removed only containers matching the predicate
        verify(dockerService).stopRunningContainer(preComputeContainerName);
        verify(dockerService).stopRunningContainer(computeContainerName);
        verify(dockerService).stopRunningContainer(postComputeContainerName);
    }

    //#endregion

    //#region stopRunningContainer

    @Test
    void shouldStopRunningContainer() {
        String containerName = "containerName";
        // Add container to record
        dockerService.addToRunningContainersRecord(containerName);
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.isContainerPresent(containerName)).thenReturn(true);
        when(dockerClientInstanceMock.isContainerActive(containerName)).thenReturn(true);
        when(dockerClientInstanceMock.stopContainer(containerName)).thenReturn(true);

        dockerService.stopRunningContainer(containerName);
        verify(dockerClientInstanceMock).isContainerPresent(containerName);
        verify(dockerClientInstanceMock).isContainerActive(containerName);
        verify(dockerClientInstanceMock).stopContainer(containerName);
        verify(dockerClientInstanceMock, never()).removeContainer(containerName);
        verify(dockerService).removeFromRunningContainersRecord(containerName);
    }

    @Test
    void shouldNotStopRunningContainerSinceNotFound() {
        String containerName = "containerName";
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.isContainerPresent(containerName)).thenReturn(false);

        dockerService.stopRunningContainer(containerName);
        verify(dockerClientInstanceMock).isContainerPresent(containerName);
        verify(dockerClientInstanceMock, never()).isContainerActive(containerName);
        verify(dockerClientInstanceMock, never()).stopContainer(containerName);
        verify(dockerClientInstanceMock, never()).removeContainer(containerName);
        verify(dockerService, never()).removeFromRunningContainersRecord(containerName);
    }

    @Test
    void shouldNotStopRunningContainerButRemoveRecordSinceNotActive() {
        String containerName = "containerName";
        // Add container to record
        dockerService.addToRunningContainersRecord(containerName);
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.isContainerPresent(containerName)).thenReturn(true);
        when(dockerClientInstanceMock.isContainerActive(containerName)).thenReturn(false);

        dockerService.stopRunningContainer(containerName);
        verify(dockerClientInstanceMock).isContainerPresent(containerName);
        verify(dockerClientInstanceMock).isContainerActive(containerName);
        verify(dockerClientInstanceMock, never()).stopContainer(containerName);
        verify(dockerClientInstanceMock, never()).removeContainer(containerName);
        verify(dockerService).removeFromRunningContainersRecord(containerName);
    }

    @Test
    void shouldTryToStopRunningContainerButNotRemoveRecordSinceStopFailed() {
        String containerName = "containerName";
        // Add container to record
        dockerService.addToRunningContainersRecord(containerName);
        doReturn(dockerClientInstanceMock).when(dockerService).getClient();
        when(dockerClientInstanceMock.isContainerPresent(containerName)).thenReturn(true);
        when(dockerClientInstanceMock.isContainerActive(containerName)).thenReturn(true);
        when(dockerClientInstanceMock.stopContainer(containerName)).thenReturn(false);

        dockerService.stopRunningContainer(containerName);
        verify(dockerClientInstanceMock).isContainerPresent(containerName);
        verify(dockerClientInstanceMock).isContainerActive(containerName);
        verify(dockerClientInstanceMock).stopContainer(containerName);
        verify(dockerClientInstanceMock, never()).removeContainer(containerName);
        verify(dockerService, never()).removeFromRunningContainersRecord(containerName);
    }

    //#endregion

    //#region addToRunningContainersRecord()

    @Test
    void shouldAddToRunningContainersRecord() {
        String containerName = "containerName";
        Assertions.assertThat(dockerService
                .addToRunningContainersRecord(containerName)).isTrue();
    }

    @Test
    void shouldNotAddToRunningContainersRecord() {
        String containerName = "containerName";
        dockerService.addToRunningContainersRecord(containerName);
        //add already existing name
        Assertions.assertThat(dockerService
                .addToRunningContainersRecord(containerName)).isFalse();
    }

    //#endregion

    //#region removeFromRunningContainersRecord()

    @Test
    void shouldRemoveFromRunningContainersRecord() {
        String containerName = "containerName";
        // Add container to records
        assertThat(dockerService.getRunningContainersRecord()).isEmpty();
        dockerService.addToRunningContainersRecord(containerName);
        assertThat(dockerService.getRunningContainersRecord()).hasSize(1);
        boolean isRemoved = dockerService.removeFromRunningContainersRecord(containerName);
        assertThat(isRemoved).isTrue();
        assertThat(dockerService.getRunningContainersRecord()).isEmpty();
    }

    @Test
    void shouldNotRemoveFromRunningContainersRecord() {
        String containerName = "containerName";

        Assertions.assertThat(dockerService
                .removeFromRunningContainersRecord(containerName)).isFalse();
    }

    //#endregion
}
