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

import com.google.common.collect.ImmutableList;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerClientService;
import com.iexec.worker.docker.DockerRunRequest;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.Device;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


public class DockerServiceTests {

    @InjectMocks
    private DockerService dockerService;
    @Mock
    private final DockerClientService dockerClientService;
    private final WorkerConfigurationService workerConfigService;

    private static final String TEST_WORKER = "./src/test/resources/tmp/test-worker";
    private static final String CHAIN_TASK_ID = "docker";
    private static String DOCKER_TMP_FOLDER = "";
    private static final long SECOND = 1000;

    private static final String ALPINE = "alpine";
    private static final String ALPINE_LATEST = "alpine:latest";
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String CMD = "cmd";
    private static final List<String> ENV = Arrays.asList("FOO=bar");

    private static final String SGX_DEVICE_PATH = "/dev/isgx";
    private static final String SGX_DEVICE_PERMISSIONS = "rwm";

    @BeforeClass
    public static void beforeClass() {
        DOCKER_TMP_FOLDER = new File(TEST_WORKER + "/" + CHAIN_TASK_ID).getAbsolutePath();
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    private String getDockerInput() { return DOCKER_TMP_FOLDER + FileHelper.SLASH_INPUT; }
    private String getDockerOutput() { return DOCKER_TMP_FOLDER + FileHelper.SLASH_OUTPUT; }

    public DockerRunRequest getAppDockerComputeStub(boolean isSgx) {
        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put(getDockerInput(), FileHelper.SLASH_IEXEC_IN);
        bindPaths.put(getDockerOutput(), FileHelper.SLASH_IEXEC_OUT);
        return DockerRunRequest.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .binds(bindPaths)
                .isSgx(isSgx)
                .maxExecutionTime(500000)
                .build();
    }

    // docker image

    @Test
    public void shouldPullImage() {
        boolean imagePulled = dockerService.pullImage(CHAIN_TASK_ID, ALPINE_LATEST);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageWithWrongName() {
        boolean imagePulled = dockerService.pullImage(CHAIN_TASK_ID, BLABLA_LATEST);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithWrongTag() {
        boolean imagePulled = dockerService.pullImage(CHAIN_TASK_ID, ALPINE_BLABLA);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithoutImageName() {
        boolean imagePulled = dockerService.pullImage(CHAIN_TASK_ID, "");
        assertThat(imagePulled).isFalse();
    }

    // shouldn't we refuse apps without tags ??
    @Ignore
    @Test
    public void shouldPullLatestImageWithoutTag() {
        boolean imagePulled = dockerService.pullImage(CHAIN_TASK_ID, ALPINE);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldIsImagePulledReturnTrue() {
        boolean pullResult = dockerService.pullImage(CHAIN_TASK_ID, ALPINE_LATEST);
        boolean isImagePulled = dockerService.isImagePulled(ALPINE_LATEST);
        assertThat(pullResult).isTrue();
        assertThat(isImagePulled).isTrue();
    }

    @Test
    public void shouldIsImagePulledReturnFalse() {
        boolean pullResult = dockerService.pullImage(CHAIN_TASK_ID, ALPINE_BLABLA);
        boolean isImagePulled = dockerService.isImagePulled(ALPINE_BLABLA);
        assertThat(pullResult).isFalse();
        assertThat(isImagePulled).isFalse();
    }
    
    // buildContainerConfig()

    @Test
    public void shouldBuildNonTeeAppContainerConfig() {
        DockerRunRequest dockerRunRequest = getAppDockerComputeStub(false);
        ContainerConfig containerConfig = 
                dockerService.buildContainerConfig(dockerRunRequest).get();

        assertThat(containerConfig.image()).isEqualTo(ALPINE_LATEST);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);
        assertThat(containerConfig.hostConfig().devices()).isNull();
        ImmutableList<String> binds = containerConfig.hostConfig().binds();
        assertThat(binds).contains(
                getDockerInput()  + ":" + FileHelper.SLASH_IEXEC_IN,
                getDockerOutput() + ":" + FileHelper.SLASH_IEXEC_OUT);
    }

    @Test
    public void shouldBuildTeeAppContainerConfig() {
        DockerRunRequest dockerRunRequest = getAppDockerComputeStub(true);
        ContainerConfig containerConfig = 
                dockerService.buildContainerConfig(dockerRunRequest).get();

        assertThat(containerConfig.image()).isEqualTo(ALPINE_LATEST);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);
        ImmutableList<String> binds = containerConfig.hostConfig().binds();
        assertThat(binds).contains(
                getDockerInput()  + ":" + FileHelper.SLASH_IEXEC_IN,
                getDockerOutput() + ":" + FileHelper.SLASH_IEXEC_OUT);
        Device sgxDevice = containerConfig.hostConfig().devices().get(0);
        assertThat(sgxDevice.pathOnHost()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.pathInContainer()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.cgroupPermissions()).isEqualTo(SGX_DEVICE_PERMISSIONS);
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutImage() {
        DockerRunRequest config = getAppDockerComputeStub(false);
        config.setImageUri("");
        Optional<ContainerConfig> containerConfig = 
                dockerService.buildContainerConfig(config);

        assertThat(containerConfig).isEmpty();
    }

    // execute()

    @Test
    public void shouldExecute() {
        DockerRunRequest config = getAppDockerComputeStub(false);
        config.setCmd("echo Hello from Docker alpine!");
        Optional<String> oStdout = dockerService.run(config);
        assertThat(oStdout.get()).contains("Hello from Docker alpine!");
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerRunRequest config = getAppDockerComputeStub(false);
        config.setCmd(cmd);
        config.setMaxExecutionTime(5 * SECOND);
        Optional<String> oStdout = dockerService.run(config);
        assertThat(oStdout.get()).isEmpty();
    }

    // createContainer()

    @Test
    public void shouldNotCreateContainerWithNullConfig() {
        String containerId = dockerService.createContainer(CHAIN_TASK_ID, null);
        assertThat(containerId).isEmpty();
    }

    // startContainer()

    @Test
    public void shouldNotStartContainerWithEmptyId() {
        boolean isStarted = dockerService.startContainer("");
        assertThat(isStarted).isFalse();
    }

    @Test
    public void shouldNotStartContainerWithBadId() {
        boolean isStarted = dockerService.startContainer("blabla");
        assertThat(isStarted).isFalse();
    }

    // stopContainer()

    @Test
    public void shouldNotStopContainerWithEmptyId() {
        boolean isStopped = dockerService.stopContainer("");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldNotStopContainerWithBadId() {
        boolean isStopped = dockerService.stopContainer("blabla");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldStopAlreadyStoppedContainer() {
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(ALPINE_LATEST)
                .build();
        String containerId = dockerService.createContainer(CHAIN_TASK_ID, containerConfig);

        assertThat(containerId).isNotEmpty();
        boolean isStopped = dockerService.stopContainer(containerId);
        assertThat(isStopped).isTrue();
        dockerService.removeContainer(containerId);
    }

    // getContainerLogs()

    @Test
    public void shouldNotGetLogsOfContainerWithEmptyId() {
        Optional<String> dockerLogs = dockerService.getContainerLogs("");
        assertThat(dockerLogs).isEmpty();;
    }

    @Test
    public void shouldNotGetLogsOfContainerWithBadId() {
        Optional<String> dockerLogs = dockerService.getContainerLogs(CHAIN_TASK_ID);
        assertThat(dockerLogs).isEmpty();
    }

    // removeContainer()

    @Test
    public void shouldNotRemoveRunningContainer() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerRunRequest config = getAppDockerComputeStub(false);
        config.setCmd(cmd);

        ContainerConfig containerConfig = 
                dockerService.buildContainerConfig(config).get();

        String containerId = dockerService.createContainer(CHAIN_TASK_ID, containerConfig);

        assertThat(containerId).isNotEmpty();

        boolean isStarted = dockerService.startContainer(containerId);
        assertThat(isStarted).isTrue();

        boolean isRemoved = dockerService.removeContainer(containerId);
        assertThat(isRemoved).isFalse();

        dockerService.stopContainer(containerId);
        boolean isRemovedAfterStopped = dockerService.removeContainer(containerId);
        assertThat(isRemovedAfterStopped).isTrue();
    }

    @Test
    public void shouldNotRemoveContainerWithEmptyId() {
        boolean isRemoved = dockerService.removeContainer("");
        assertThat(isRemoved).isFalse();
    }

    @Test
    public void shouldNotRemoveContainerWithBadId() {
        boolean isRemoved = dockerService.removeContainer("blabla");
        assertThat(isRemoved).isFalse();
    }
}
