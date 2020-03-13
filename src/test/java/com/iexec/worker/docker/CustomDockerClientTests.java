package com.iexec.worker.docker;

import com.google.common.collect.ImmutableList;
import com.iexec.common.utils.FileHelper;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.Device;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


public class CustomDockerClientTests {

    @InjectMocks
    private CustomDockerClient customDockerClient;

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

    public String getDockerInput() { return DOCKER_TMP_FOLDER + "/input"; }
    public String getDockerOutput() { return DOCKER_TMP_FOLDER + "/output"; }
    public String getDockerIexecOut() { return getDockerOutput() + "/iexec_out"; }
    public String getDockerScone() { return DOCKER_TMP_FOLDER + "/scone"; }

    public DockerExecutionConfig getDockerExecutionConfigStub() {
        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put(getDockerInput(), FileHelper.SLASH_IEXEC_IN);
        bindPaths.put(getDockerIexecOut(), FileHelper.SLASH_IEXEC_OUT);

        return DockerExecutionConfig.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .imageUri(ALPINE_LATEST)
                .cmd(CMD)
                .env(ENV)
                .bindPaths(bindPaths)
                .isSgx(false)
                .maxExecutionTime(500000)
                .build();
    }

    // docker image

    @Test
    public void shouldPullImage() {
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, ALPINE_LATEST);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageWithWrongName() {
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, BLABLA_LATEST);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithWrongTag() {
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, ALPINE_BLABLA);
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithoutImageName() {
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, "");
        assertThat(imagePulled).isFalse();
    }

    // shouldn't we refuse apps without tags ??
    @Ignore
    @Test
    public void shouldPullLatestImageWithoutTag() {
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, ALPINE);
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldIsImagePulledReturnTrue() {
        boolean pullResult = customDockerClient.pullImage(CHAIN_TASK_ID, ALPINE_LATEST);
        boolean isImagePulled = customDockerClient.isImagePulled(ALPINE_LATEST);
        assertThat(pullResult).isTrue();
        assertThat(isImagePulled).isTrue();
    }

    @Test
    public void shouldIsImagePulledReturnFalse() {
        boolean pullResult = customDockerClient.pullImage(CHAIN_TASK_ID, ALPINE_BLABLA);
        boolean isImagePulled = customDockerClient.isImagePulled(ALPINE_BLABLA);
        assertThat(pullResult).isFalse();
        assertThat(isImagePulled).isFalse();
    }
    
    // buildContainerConfig()

    @Test
    public void shouldBuildContainerConfigWithoutSgxDevice() {
        DockerExecutionConfig config = getDockerExecutionConfigStub();

        ContainerConfig containerConfig = 
                customDockerClient.buildContainerConfig(config).get();

        assertThat(containerConfig.image()).isEqualTo(ALPINE_LATEST);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);
        assertThat(containerConfig.hostConfig().devices()).isNull();

        ImmutableList<String> binds = containerConfig.hostConfig().binds();
        assertThat(binds).contains(
                getDockerInput() + ":/iexec_in",
                getDockerIexecOut() + ":/iexec_out");
    }

    @Test
    public void shouldBuildContainerConfigWithSgxDevice() {
        DockerExecutionConfig config = getDockerExecutionConfigStub();
        config.setSgx(true);
        config.getBindPaths().put(getDockerScone(), FileHelper.SLASH_SCONE);

        ContainerConfig containerConfig = 
                customDockerClient.buildContainerConfig(config).get();

        assertThat(containerConfig.image()).isEqualTo(ALPINE_LATEST);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);

        ImmutableList<String> binds = containerConfig.hostConfig().binds();
        assertThat(binds).contains(
                getDockerInput() + ":/iexec_in",
                getDockerIexecOut() + ":/iexec_out",
                getDockerScone()+ ":/scone");

        Device sgxDevice = containerConfig.hostConfig().devices().get(0);
        assertThat(sgxDevice.pathOnHost()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.pathInContainer()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.cgroupPermissions()).isEqualTo(SGX_DEVICE_PERMISSIONS);
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutImage() {
        DockerExecutionConfig config = getDockerExecutionConfigStub();
        config.setImageUri("");

        Optional<ContainerConfig> containerConfig = 
                customDockerClient.buildContainerConfig(config);

        assertThat(containerConfig).isEmpty();
    }

    // execute()

    @Test
    public void shouldExecute() {
        DockerExecutionConfig config = getDockerExecutionConfigStub();
        config.setCmd("echo Hello from Docker alpine!");

        DockerExecutionResult dockerExecutionResult = customDockerClient.execute(config);
        assertThat(dockerExecutionResult.getStdout()).contains("Hello from Docker alpine!");
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerExecutionConfig config = getDockerExecutionConfigStub();
        config.setCmd(cmd);
        config.setMaxExecutionTime(5 * SECOND);

        DockerExecutionResult dockerExecutionResult = customDockerClient.execute(config);
        assertThat(dockerExecutionResult.getStdout()).isEmpty();
    }

    // createContainer()

    @Test
    public void shouldNotCreateContainerWithNullConfig() {
        Optional<CustomContainerInfo> containerInfo =
                customDockerClient.createContainer(CHAIN_TASK_ID, null);
        assertThat(containerInfo).isEmpty();
    }

    // startContainer()

    @Test
    public void shouldNotStartContainerWithEmptyId() {
        boolean isStarted = customDockerClient.startContainer("");
        assertThat(isStarted).isFalse();
    }

    @Test
    public void shouldNotStartContainerWithBadId() {
        boolean isStarted = customDockerClient.startContainer("blabla");
        assertThat(isStarted).isFalse();
    }

    // stopContainer()

    @Test
    public void shouldNotStopContainerWithEmptyId() {
        boolean isStopped = customDockerClient.stopContainer("");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldNotStopContainerWithBadId() {
        boolean isStopped = customDockerClient.stopContainer("blabla");
        assertThat(isStopped).isFalse();
    }

    @Test
    public void shouldStopAlreadyStoppedContainer() {
        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(ALPINE_LATEST)
                .build();

        CustomContainerInfo containerInfo =
                customDockerClient.createContainer(CHAIN_TASK_ID, containerConfig).get();

        String containerId = containerInfo.getContainerId();
        assertThat(containerId).isNotEmpty();
        boolean isStopped = customDockerClient.stopContainer(containerId);
        assertThat(isStopped).isTrue();
        customDockerClient.removeContainer(containerId);
    }

    // getContainerLogs()

    @Test
    public void shouldNotGetLogsOfContainerWithEmptyId() {
        Optional<String> dockerLogs = customDockerClient.getContainerLogs("");
        assertThat(dockerLogs).isEmpty();;
    }

    @Test
    public void shouldNotGetLogsOfContainerWithBadId() {
        Optional<String> dockerLogs = customDockerClient.getContainerLogs(CHAIN_TASK_ID);
        assertThat(dockerLogs).isEmpty();
    }

    // removeContainer()

    @Test
    public void shouldNotRemoveRunningContainer() {
        String cmd = "sh -c 'sleep 10 && echo Hello from Docker alpine!'";
        DockerExecutionConfig config = getDockerExecutionConfigStub();
        config.setCmd(cmd);

        ContainerConfig containerConfig = 
                customDockerClient.buildContainerConfig(config).get();

        CustomContainerInfo containerInfo =
                customDockerClient.createContainer(CHAIN_TASK_ID, containerConfig).get();

        String containerId = containerInfo.getContainerId();
        assertThat(containerId).isNotEmpty();

        boolean isStarted = customDockerClient.startContainer(containerId);
        assertThat(isStarted).isTrue();

        boolean isRemoved = customDockerClient.removeContainer(containerId);
        assertThat(isRemoved).isFalse();

        customDockerClient.stopContainer(containerId);
        boolean isRemovedAfterStopped = customDockerClient.removeContainer(containerId);
        assertThat(isRemovedAfterStopped).isTrue();
    }

    @Test
    public void shouldNotRemoveContainerWithEmptyId() {
        boolean isRemoved = customDockerClient.removeContainer("");
        assertThat(isRemoved).isFalse();
    }

    @Test
    public void shouldNotRemoveContainerWithBadId() {
        boolean isRemoved = customDockerClient.removeContainer("blabla");
        assertThat(isRemoved).isFalse();
    }
}
