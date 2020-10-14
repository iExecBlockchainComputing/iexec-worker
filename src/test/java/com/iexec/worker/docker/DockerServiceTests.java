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

import com.iexec.worker.config.WorkerConfigurationService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;


public class DockerServiceTests {

    @InjectMocks
    private DockerService dockerService;
    @Mock
    private DockerClientService dockerClientService;
    @Mock
    private WorkerConfigurationService workerConfigService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldRun() {
        String containerId = "containerId";
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn(containerId);
        when(dockerClientService.startContainer(containerId)).thenReturn(true);
        when(dockerClientService.stopContainer(containerId)).thenReturn(true);
        when(dockerClientService.getContainerLogs(containerId)).thenReturn(Optional.of(
                DockerLogs.builder().stdout("stdout").stderr("stderr").build()));
        when(dockerClientService.removeContainer(containerId)).thenReturn(true);

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isTrue();
        Assertions.assertThat(dockerRunResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(dockerRunResponse.getDockerLogs().getStdout()).isEqualTo("stdout");
        Assertions.assertThat(dockerRunResponse.getDockerLogs().getStderr()).isEqualTo("stderr");

        verify(dockerClientService, times(1))
                .waitContainerUntilExitOrTimeout(anyString(), any());
    }

    @Test
    public void shouldRunWithoutWaiting() {
        String containerId = "containerId";
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(0)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn(containerId);
        when(dockerClientService.startContainer(containerId)).thenReturn(true);

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isTrue();
        Assertions.assertThat(dockerRunResponse.getStdout()).isEmpty();
    }

    @Test
    public void shouldNotRunSinceCantCreateContainer() {
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn("");

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isFalse();
    }

    @Test
    public void shouldNotRunSinceCantStartContainer() {
        String containerId = "containerId";
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn(containerId);
        when(dockerClientService.startContainer(containerId)).thenReturn(false);

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isFalse();
        verify(dockerClientService, times(1))
                .removeContainer(containerId);
    }

    @Test
    public void shouldNotRunSinceCantStopContainer() {
        String containerId = "containerId";
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn(containerId);
        when(dockerClientService.startContainer(containerId)).thenReturn(true);
        when(dockerClientService.stopContainer(containerId)).thenReturn(false);

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isFalse();
    }

    @Test
    public void shouldNotRunSinceCantRemoveContainer() {
        String containerId = "containerId";
        String containerName = "containerName";
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .maxExecutionTime(5000)
                .build();
        when(dockerClientService.createContainer(dockerRunRequest))
                .thenReturn(containerId);
        when(dockerClientService.startContainer(containerId)).thenReturn(true);
        when(dockerClientService.stopContainer(containerId)).thenReturn(true);
        when(dockerClientService.removeContainer(containerId)).thenReturn(false);

        DockerRunResponse dockerRunResponse =
                dockerService.run(dockerRunRequest);

        Assertions.assertThat(dockerRunResponse).isNotNull();
        Assertions.assertThat(dockerRunResponse.isSuccessful()).isFalse();
    }

    @Test
    public void shouldPullImage() {
        String image = "image";
        when(dockerClientService.pullImage(image)).thenReturn(true);

        Assertions.assertThat(dockerService.pullImage(image)).isTrue();
    }

    @Test
    public void shouldNotPullImage() {
        String image = "image";
        when(dockerClientService.pullImage(image)).thenReturn(false);

        Assertions.assertThat(dockerService.pullImage(image)).isFalse();
    }

    @Test
    public void shouldHaveImagePulled() {
        String image = "image";
        when(dockerClientService.getImageId(image)).thenReturn("imageId");

        Assertions.assertThat(dockerService.isImagePulled(image)).isTrue();
    }

    @Test
    public void shouldNotHaveImagePulled() {
        String image = "image";
        when(dockerClientService.getImageId(image)).thenReturn("");

        Assertions.assertThat(dockerService.isImagePulled(image)).isFalse();
    }


    @Test
    public void shouldStopAndRemoveContainer() {
        String containerName = "containerName";
        when(dockerClientService.stopContainer(containerName)).thenReturn(true);
        when(dockerClientService.removeContainer(containerName)).thenReturn(true);

        Assertions.assertThat(dockerService.stopAndRemoveContainer(containerName)).isTrue();
    }

    @Test
    public void shouldStopAndRemoveContainerSinceCantStop() {
        String containerName = "containerName";
        when(dockerClientService.stopContainer(containerName)).thenReturn(false);

        Assertions.assertThat(dockerService.stopAndRemoveContainer(containerName)).isFalse();
    }

    @Test
    public void shouldStopAndRemoveContainerSinceCantRemove() {
        String containerName = "containerName";
        when(dockerClientService.stopContainer(containerName)).thenReturn(true);
        when(dockerClientService.removeContainer(containerName)).thenReturn(false);

        Assertions.assertThat(dockerService.stopAndRemoveContainer(containerName)).isFalse();
    }

    @Test
    public void shouldPrintDeveloperLogs() {
        DockerRunRequest dockerRunRequest =
                DockerRunRequest.builder().shouldDisplayLogs(true).build();
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(true);

        Assertions.assertThat(dockerService.shouldPrintDeveloperLogs(dockerRunRequest)).isTrue();
    }

    @Test
    public void shouldNotPrintDeveloperLogsSinceRequestDoNotAllowIt() {
        DockerRunRequest dockerRunRequest =
                DockerRunRequest.builder().shouldDisplayLogs(false).build();
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(true);

        Assertions.assertThat(dockerService.shouldPrintDeveloperLogs(dockerRunRequest)).isFalse();
    }

    @Test
    public void shouldNotPrintDeveloperLogsSinceWorkerDoNotAllowIt() {
        DockerRunRequest dockerRunRequest =
                DockerRunRequest.builder().shouldDisplayLogs(true).build();
        when(workerConfigService.isDeveloperLoggerEnabled()).thenReturn(false);

        Assertions.assertThat(dockerService.shouldPrintDeveloperLogs(dockerRunRequest)).isFalse();
    }

}
