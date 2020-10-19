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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.iexec.common.utils.ArgsUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.sgx.SgxService;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static com.iexec.worker.docker.DockerClientService.WORKER_DOCKER_NETWORK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class DockerClientServiceTests {

    private static final String CHAIN_TASK_ID = "docker";
    private static final String ALPINE_LATEST = "alpine:latest";
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String CMD = "cmd";
    private static final List<String> ENV = Arrays.asList("FOO=bar");

    @InjectMocks
    private DockerClientService dockerClientService;
    @Mock
    private DockerConnectorService dockerConnectorService;
    private DockerClient fakeDockerClient;


    @BeforeClass
    public static void beforeClass() {
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        useRealDockerClient();
    }

    public DockerRunRequest getDefaultDockerRunRequest(boolean isSgx) {
        return DockerRunRequest.builder()
                .containerName(getRandomString())
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .containerPort(1000)
                .binds(Collections.singletonList(FileHelper.SLASH_IEXEC_IN +
                        ":" + FileHelper.SLASH_IEXEC_OUT))
                .isSgx(isSgx)
                .maxExecutionTime(500000)
                .build();
    }


    // docker network

    @Test
    public void shouldCreateNetwork() {
        String networkName = getRandomString();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(networkId).isNotEmpty();

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldNotCreateNetworkSinceEmptyName() {
        assertThat(dockerClientService.createNetwork("")).isEmpty();
    }

    @Test
    public void shouldNotCreateNetworkSinceExisting() {
        String networkName = getRandomString();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(dockerClientService.createNetwork(networkName)).isEmpty();

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldNotCreateNetworkSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.createNetwork(getRandomString())).isEmpty();
    }

    @Test
    public void shouldGetNetworkId() {
        String networkName = getRandomString();
        String networkId = dockerClientService.createNetwork(networkName);

        assertThat(dockerClientService.getNetworkId(networkName)).isEqualTo(networkId);

        // cleaning
        dockerClientService.removeNetwork(networkId);
    }

    @Test
    public void shouldNotGetNetworkIdSinceEmptyName() {
        assertThat(dockerClientService.getNetworkId("")).isEmpty();
    }

    @Test
    public void shouldNotGetNetworkIdSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.getNetworkId(getRandomString())).isEmpty();
    }

    @Test
    public void shouldRemoveNetwork() {
        String networkId = dockerClientService.createNetwork(getRandomString());

        assertThat(dockerClientService.removeNetwork(networkId)).isTrue();
    }

    @Test
    public void shouldNotRemoveNetworkSinceEmptyId() {
        assertThat(dockerClientService.removeNetwork("")).isFalse();
    }

    @Test
    public void shouldNotRemoveNetworkSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.removeNetwork(getRandomString())).isFalse();
    }


    // docker image
    @Test
    public void shouldPullImage() {
        boolean imagePulled = dockerClientService.pullImage(ALPINE_LATEST);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageSinceEmptyName() {
        boolean imagePulled = dockerClientService.pullImage("");
        assertThat(imagePulled).isFalse();
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
    public void shouldNotPullImageSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.pullImage(getRandomString())).isFalse();
    }

    @Test
    public void shouldGetImageId() {
        dockerClientService.pullImage(ALPINE_LATEST);

        assertThat(dockerClientService.getImageId(ALPINE_LATEST)).isNotEmpty();
    }

    @Test
    public void shouldNotGetImageIdSinceEmptyName() {
        assertThat(dockerClientService.getImageId("")).isEmpty();
    }

    @Test
    public void shouldNotGetImageId() {
        assertThat(dockerClientService.getImageId(BLABLA_LATEST)).isEmpty();
    }

    @Test
    public void shouldNotGetImageIdSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.getImageId(getRandomString())).isEmpty();
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
    public void shouldNotCreateContainerSinceNoRequest() {
        assertThat(dockerClientService.createContainer(null)).isEmpty();
    }

    @Test
    public void shouldNotCreateContainerSinceEmptyContainerName() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setContainerName("");

        assertThat(dockerClientService.createContainer(request)).isEmpty();
    }

    @Test
    public void shouldNotCreateContainerSinceEmptyImageUri() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setImageUri("");

        assertThat(dockerClientService.createContainer(request)).isEmpty();
    }

    @Test
    public void shouldNotCreateContainerSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        useFakeDockerClient();
        assertThat(dockerClientService.createContainer(request)).isEmpty();
    }

    @Test
    public void shouldBuildCreateContainerHostConfig() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);

        HostConfig hostConfig = dockerClientService.buildCreateContainerHostConfig(request);
        Assertions.assertThat(hostConfig.getNetworkMode()).isEqualTo(WORKER_DOCKER_NETWORK);
        Assertions.assertThat((hostConfig.getBinds()[0].getPath())).isEqualTo(FileHelper.SLASH_IEXEC_IN);
        Assertions.assertThat((hostConfig.getBinds()[0].getVolume().getPath())).isEqualTo(FileHelper.SLASH_IEXEC_OUT);
        Assertions.assertThat(hostConfig.getDevices()).isNull();
    }

    @Test
    public void shouldBuildCreateContainerHostConfigWithSgx() {
        DockerRunRequest request = getDefaultDockerRunRequest(true);

        HostConfig hostConfig = dockerClientService.buildCreateContainerHostConfig(request);
        Assertions.assertThat(hostConfig.getNetworkMode()).isEqualTo(WORKER_DOCKER_NETWORK);
        Assertions.assertThat((hostConfig.getBinds()[0].getPath())).isEqualTo(FileHelper.SLASH_IEXEC_IN);
        Assertions.assertThat((hostConfig.getBinds()[0].getVolume().getPath())).isEqualTo(FileHelper.SLASH_IEXEC_OUT);
        Assertions.assertThat(hostConfig.getDevices()[0].getPathOnHost()).isEqualTo(SgxService.SGX_DEVICE_PATH);
        Assertions.assertThat(hostConfig.getDevices()[0].getPathInContainer()).isEqualTo(SgxService.SGX_DEVICE_PATH);
    }

    @Test
    public void shouldGetRequestedCreateContainerCmd() {
        CreateContainerCmd createContainerCmd = getRealDockerClient()
                .createContainerCmd("repo/image:tag");
        DockerRunRequest request = getDefaultDockerRunRequest(false);

        CreateContainerCmd actualCreateContainerCmd =
                dockerClientService.getRequestedCreateContainerCmd(request,
                        createContainerCmd);
        Assertions.assertThat(actualCreateContainerCmd.getName())
                .isEqualTo(request.getContainerName());
        Assertions.assertThat(actualCreateContainerCmd.getHostConfig())
                .isEqualTo(dockerClientService.buildCreateContainerHostConfig(request));
        Assertions.assertThat(actualCreateContainerCmd.getCmd())
                .isEqualTo(ArgsUtils.stringArgsToArrayArgs(request.getCmd()));
        Assertions.assertThat(actualCreateContainerCmd.getEnv()).isNotNull();
        Assertions.assertThat(Arrays.asList(actualCreateContainerCmd.getEnv()))
                .isEqualTo(request.getEnv());
        Assertions.assertThat(actualCreateContainerCmd.getExposedPorts()).isNotNull();
        Assertions.assertThat(actualCreateContainerCmd.getExposedPorts()[0].getPort())
                .isEqualTo(1000);
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
    public void shouldNotGetImageIdSinceEmptyId() {
        assertThat(dockerClientService.getContainerId("")).isEmpty();
    }

    @Test
    public void shouldNotGetContainerIdSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.getContainerId(getRandomString())).isEmpty();
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
    public void shouldNotGetContainerStatusSinceEmptyId() {
        assertThat(dockerClientService.getContainerStatus("")).isEmpty();
    }

    @Test
    public void shouldGetContainerName() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String containerId = dockerClientService.createContainer(request);

        assertThat(dockerClientService.getContainerName(containerId))
                .isEqualTo(request.getContainerName());

        // cleaning
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldNotGetContainerNameSinceEmptyId() {
        assertThat(dockerClientService.getContainerName("")).isEmpty();
    }

    @Test
    public void shouldNotGetContainerNameSinceNoContainer() {
        assertThat(dockerClientService.getContainerName(getRandomString())).isEmpty();
    }

    @Test
    public void shouldNotGetContainerNameSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        String containerId = dockerClientService.createContainer(request);

        useFakeDockerClient();
        assertThat(dockerClientService.getContainerName(getRandomString())).isEmpty();

        // cleaning
        useRealDockerClient();
        dockerClientService.removeContainer(containerId);
    }

    @Test
    public void shouldNotGetContainerStatusSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.getContainerStatus(getRandomString())).isEmpty();
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
    public void shouldNotStartContainerNameSinceEmptyId() {
        assertThat(dockerClientService.startContainer("")).isFalse();
    }

    @Test
    public void shouldNotStartContainerSinceDockerCmdException() {
        DockerRunRequest request = getDefaultDockerRunRequest(false);
        request.setCmd("sh -c 'sleep 1 && echo Hello from Docker alpine!'");
        String containerId = dockerClientService.createContainer(request);

        useFakeDockerClient();
        assertThat(dockerClientService.startContainer(containerId)).isFalse();

        // cleaning
        useRealDockerClient();
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
    public void shouldNotGetContainerLogsSinceEmptyId() {
        assertThat(dockerClientService.getContainerLogs("")).isEmpty();
    }

    @Test
    public void shouldNotGetContainerLogsSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.getContainerLogs(getRandomString())).isEmpty();
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
    public void shouldNotStopContainerSinceEmptyId() {
        assertThat(dockerClientService.stopContainer("")).isFalse();
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
    public void shouldNotRemoveContainerSinceEmptyId() {
        assertThat(dockerClientService.removeContainer("")).isFalse();
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

    @Test
    public void shouldNotRemoveContainerSinceDockerCmdException() {
        useFakeDockerClient();
        assertThat(dockerClientService.removeContainer(getRandomString())).isFalse();
    }

    private String getRandomString() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    private void useRealDockerClient() {
        when(dockerConnectorService.getClient()).thenCallRealMethod();
    }

    private void useFakeDockerClient() {
        when(dockerConnectorService.getClient()).thenReturn(getFakeDockerClient());
    }

    private DockerClient getFakeDockerClient() {
        if (fakeDockerClient == null) {
            DockerClientConfig config =
                    DefaultDockerClientConfig.createDefaultConfigBuilder()
                            .withDockerHost("tcp://localhost:11111")
                            .build();
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();
            fakeDockerClient = DockerClientImpl.getInstance(config, httpClient);
        }
        return fakeDockerClient;
    }

    private DockerClient getRealDockerClient() {
        DockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

}
