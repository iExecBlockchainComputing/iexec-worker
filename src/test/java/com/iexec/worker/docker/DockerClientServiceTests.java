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

    private static final String TEST_WORKER = "./src/test/resources/tmp/test-worker";
    private static final String CHAIN_TASK_ID = "docker";
    private static final String ALPINE_LATEST = "alpine:latest";
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String CMD = "cmd";
    private static final List<String> ENV = Arrays.asList("FOO=bar");
    private static final String SGX_DEVICE_PATH = "/dev/isgx";
    private static final String SGX_DEVICE_PERMISSIONS = "rwm";
    private static String DOCKER_TMP_FOLDER = "";

    @InjectMocks
    private DockerClientService dockerClientService;


    @BeforeClass
    public static void beforeClass() {
        DOCKER_TMP_FOLDER = new File(TEST_WORKER + "/" + CHAIN_TASK_ID).getAbsolutePath();
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    private String getDockerInput() {
        return DOCKER_TMP_FOLDER + FileHelper.SLASH_INPUT;
    }

    private String getDockerOutput() {
        return DOCKER_TMP_FOLDER + FileHelper.SLASH_OUTPUT;
    }

    public DockerRunRequest getDefaultDockerRunRequest(boolean isSgx) {
        return DockerRunRequest.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .binds(Collections.singletonList(FileHelper.SLASH_IEXEC_IN + ":" + FileHelper.SLASH_IEXEC_OUT))
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
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);

        //TODO Inspect container and check requeste params are set

        String containerId = dockerClientService.createContainer(request);
        assertThat(containerId).isNotEmpty();

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerId() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String expectedId = dockerClientService.createContainer(request);

        String containerId = dockerClientService.getContainerId(containerName);
        assertThat(containerId).isNotEmpty();
        assertThat(containerId).isEqualTo(expectedId);

        // cleaning
        dockerClientService.removeContainer(expectedId);
    }

    @Test
    public void shouldGetContainerStatus() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String containerId = dockerClientService.createContainer(request);

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("created");

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldStartContainer() {
        String containerName = getRandomName();
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
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 2 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        Date before = new Date();
        dockerClientService.waitContainerUntilExitOrTimeout(containerId, new Date(new Date().getTime() + 1000));
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        assertThat(new Date().getTime() - before.getTime()).isGreaterThan(1000);

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldWaitContainerUntilExitOrTimeoutSinceExited() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        dockerClientService.waitContainerUntilExitOrTimeout(containerId, new Date(new Date().getTime() + 3000));
        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("exited");

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerLogsSinceStdout() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        Optional<DockerContainerLogs> containerLogs = dockerClientService.getContainerLogs(containerId);
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).isEqualTo("Hello from Docker alpine!");
        assertThat(containerLogs.get().getStderr()).isEmpty();

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldGetContainerLogsSinceStderr() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'echo Hello from Docker alpine! >&2'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        Optional<DockerContainerLogs> containerLogs = dockerClientService.getContainerLogs(containerId);
        assertThat(containerLogs).isPresent();
        assertThat(containerLogs.get().getStdout()).isEmpty();
        assertThat(containerLogs.get().getStderr()).isEqualTo("Hello from Docker alpine!");

        // cleaning
        dockerClientService.stopContainer(containerId);
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldStopContainer() {
        String containerName = getRandomName();
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
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);
        dockerClientService.stopContainer(containerId);

        assertThat(dockerClientService.removeContainer(containerId)).isTrue();
    }

    @Test
    public void shouldNotRemoveContainerSinceRunning() {
        String containerName = getRandomName();
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);
        dockerClientService.startContainer(containerId);

        assertThat(dockerClientService.getContainerStatus(containerId)).isEqualTo("running");
        assertThat(dockerClientService.removeContainer(containerId)).isFalse();

        // cleaning
        dockerClientService.waitContainerUntilExitOrTimeout(containerId, new Date(new Date().getTime() + 15000));
        dockerClientService.removeContainer(containerId);
    }

    /*

    @Test
    public void shouldStopComputingIfTooLong() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerRunRequest config = getDefaultDockerRunRequest(false);
        config.setCmd(cmd);
        config.setMaxExecutionTime(5 * SECOND);
        Optional<String> oStdout = dockerClientService.run(config);
        assertThat(oStdout.get()).isEmpty();
    }

    // createContainer()

    @Test
    public void shouldNotCreateContainerWithNullConfig() {
        String containerId = dockerClientService.createContainer(CHAIN_TASK_ID, null);
        assertThat(containerId).isEmpty();
    }

    // startContainer()

    @Test
    public void shouldNotStartContainerWithEmptyId() {
        boolean isStarted = dockerClientService.startContainer("");
        assertThat(isStarted).isFalse();
    }

    @Test
    public void shouldNotStartContainerWithBadId() {
        boolean isStarted = dockerClientService.startContainer("blabla");
        assertThat(isStarted).isFalse();
    }

    // stopContainer()

    @Test
    public void shouldNotStopContainerWithEmptyId() {
        boolean isStopped = dockerClientService.stopContainer("");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldNotStopContainerWithBadId() {
        boolean isStopped = dockerClientService.stopContainer("blabla");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldStopAlreadyStoppedContainer() {
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(ALPINE_LATEST)
                .build();
        String containerId = dockerClientService.createContainer(CHAIN_TASK_ID, containerConfig);

        assertThat(containerId).isNotEmpty();
        boolean isStopped = dockerClientService.stopContainer(containerId);
        assertThat(isStopped).isTrue();
        dockerClientService.removeContainer(containerId);
    }

    // getContainerLogs()

    @Test
    public void shouldNotGetLogsOfContainerWithEmptyId() {
        Optional<String> dockerLogs = dockerClientService.getContainerLogs("");
        assertThat(dockerLogs).isEmpty();;
    }

    @Test
    public void shouldNotGetLogsOfContainerWithBadId() {
        Optional<String> dockerLogs = dockerClientService.getContainerLogs(CHAIN_TASK_ID);
        assertThat(dockerLogs).isEmpty();
    }

    // removeContainer()

    @Test
    public void shouldNotRemoveRunningContainer() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerRunRequest config = getDefaultDockerRunRequest(false);
        config.setCmd(cmd);

        ContainerConfig containerConfig = 
                dockerClientService.buildContainerConfig(config).get();

        String containerId = dockerClientService.createContainer(CHAIN_TASK_ID, containerConfig);

        assertThat(containerId).isNotEmpty();

        boolean isStarted = dockerClientService.startContainer(containerId);
        assertThat(isStarted).isTrue();

        boolean isRemoved = dockerClientService.removeContainer(containerId);
        assertThat(isRemoved).isFalse();

        dockerClientService.stopContainer(containerId);
        boolean isRemovedAfterStopped = dockerClientService.removeContainer(containerId);
        assertThat(isRemovedAfterStopped).isTrue();
    }

    @Test
    public void shouldNotRemoveContainerWithEmptyId() {
        boolean isRemoved = dockerClientService.removeContainer("");
        assertThat(isRemoved).isFalse();
    }

    @Test
    public void shouldNotRemoveContainerWithBadId() {
        boolean isRemoved = dockerClientService.removeContainer("blabla");
        assertThat(isRemoved).isFalse();
    }

     */

    private String getRandomName() {
        return RandomStringUtils.randomAlphanumeric(10);
    }

}
