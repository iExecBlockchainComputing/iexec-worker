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

import com.iexec.common.utils.FileHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;


public class DockerClientServiceTests {

    private static final String CHAIN_TASK_ID = "docker";
    private static final String ALPINE_LATEST = "alpine:latest";
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String CMD = "cmd";
    private static final List<String> ENV = Arrays.asList("FOO=bar");

    @InjectMocks
    private DockerClientService dockerClientService;


    @BeforeClass
    public static void beforeClass() {
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    public DockerRunRequest getDefaultDockerRunRequest(boolean isSgx) {
        return DockerRunRequest.builder()
                .containerName(getRandomName())
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .binds(Collections.singletonList(FileHelper.SLASH_IEXEC_IN +
                        ":" + FileHelper.SLASH_IEXEC_OUT))
                .isSgx(isSgx)
                .maxExecutionTime(500000)
                .build();
    }


    // docker network

    @Test
    public void shouldCreateNetwork() {
        String networkName = getRandomName();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(networkId).isNotEmpty();

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldNotCreateNetworkSinceExisting() {
        String networkName = getRandomName();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(dockerClientService.createNetwork(networkName)).isEmpty();

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldGetNetworkId() {
        String networkName = getRandomName();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(dockerClientService.getNetworkId(networkName)).isEqualTo(networkId);

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldRemoveNetworkId() {
        String networkName = getRandomName();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(dockerClientService.removeNetwork(networkId)).isTrue();
    }

    // docker image

    @Test
    public void shouldPullImage() {
        boolean imagePulled = dockerClientService.pullImage(ALPINE_LATEST);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageSinceWrongName() {
        boolean imagePulled = dockerClientService.pullImage(BLABLA_LATEST);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageSinceWrongTag() {
        boolean imagePulled = dockerClientService.pullImage(ALPINE_BLABLA);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageSinceEmptyImageName() {
        boolean imagePulled = dockerClientService.pullImage("");
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldGetImageId() {
        dockerClientService.pullImage(ALPINE_LATEST);

        assertThat(dockerClientService.getImageId(ALPINE_LATEST)).isNotEmpty();
    }

    @Test
    public void shouldNotGetImageId() {
        assertThat(dockerClientService.getImageId(BLABLA_LATEST)).isEmpty();
    }

    // container

    @Test
    public void shouldCreateContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);

        //TODO Inspect container and check requeste params are set

        String containerId = dockerClientService.createContainer(request);
        assertThat(containerId).isNotEmpty();

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerId() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String expectedId = dockerClientService.createContainer(request);

        String containerId =
                dockerClientService.getContainerId(request.getContainerName());
        assertThat(containerId).isNotEmpty();
        assertThat(containerId).isEqualTo(expectedId);

        // cleaning
        dockerClientService.removeContainer(expectedId);
    }

    @Test
    public void shouldGetContainerStatus() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String containerId = dockerClientService.createContainer(request);

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("created");

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldStartContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);

        assertThat(dockerClientService.startContainer(containerId)).isTrue();

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldWaitContainerUntilExitOrTimeoutSinceTimeout() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 2 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        Date before = new Date();
        dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                new Date(new Date().getTime() + 1000));
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        assertThat(new Date().getTime() - before.getTime()).isGreaterThan(1000);

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldWaitContainerUntilExitOrTimeoutSinceExited() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                new Date(new Date().getTime() + 3000));
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("exited");

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerLogsSinceStdout() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        Optional<DockerLogs> containerLogs =
                dockerClientService.getContainerLogs(containerId);
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).contains("Hello from " +
                "Docker alpine!");
        assertThat(containerLogs.get().getStderr()).isEmpty();

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerLogsSinceStderr() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'echo Hello from Docker alpine! >&2'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        Optional<DockerLogs> containerLogs =
                dockerClientService.getContainerLogs(containerId);
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).isEmpty();
        assertThat(containerLogs.get().getStderr()).contains("Hello from " +
                "Docker alpine!");

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldStopContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        assertThat(dockerClientService.stopContainer(containerId)).isTrue();
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("exited");

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldRemoveContainer() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        dockerClientService.stopContainer(containerId);

        assertThat(dockerClientService.removeContainer(containerId)).isTrue();
    }

    @Test
    public void shouldNotRemoveContainerSinceRunning() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        assertThat(dockerClientService.removeContainer(containerId)).isFalse();

        // cleaning
        dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                new Date(new Date().getTime() + 15000));
        dockerClientService.removeContainer(containerId);
    }

    private String getRandomName() {
        return RandomStringUtils.randomAlphanumeric(10);
    }

}
