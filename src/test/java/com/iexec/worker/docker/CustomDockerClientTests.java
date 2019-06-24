package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.Device;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


@Slf4j
public class CustomDockerClientTests {
    // @ClassRule public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock private DefaultDockerClient docker;
    @Mock private WorkerConfigurationService workerConfigurationService;

    @InjectMocks
    private CustomDockerClient customDockerClient;

    private static final String TEST_WORKER = "./src/test/resources/tmp/test-worker";
    private static final String CHAIN_TASK_ID = "docker";
    private static String DOCKER_TMP_FOLDER = "";
    private static final long SECOND = 1000;
    private static final long MAX_EXECUTION_TIME = 10 * SECOND;

    private static final String IMAGE_URI = "image:tag";
    private static final String CMD = "cmd";
    private static final List<String> ENV = Arrays.asList("FOO=bar");

    private static final String SGX_DEVICE_PATH = "/dev/isgx";
    private static final String SGX_DEVICE_PERMISSIONS = "rwm";

    private static final String ALPINE_LATEST = "alpine:latest";
    private static final String ALPINE_BLABLA = "alpine:blabla";
    private static final String BLABLA_LATEST = "blabla:latest";
    private static final String HELLO_WORLD = "hello-world";

    @BeforeClass
    public static void beforeClass() {
        // try {
        //     DOCKER_TMP_FOLDER = tempFolder.newFolder(CHAIN_TASK_ID).getAbsolutePath();
        // } catch (Exception e) {
        //     log.error("couldn't create tmp folder");
        //     e.printStackTrace();
        // }

        DOCKER_TMP_FOLDER = new File(TEST_WORKER + CHAIN_TASK_ID).getAbsolutePath();
    }

    @Before
    public void beforeEach() throws DockerCertificateException, DockerException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        // baseDockerClient.pull("iexechub/vanityeth:latest");
        // maxExecutionTime = new Date().getTime() + 30 * SECOND;
    }

    public String getDockerInput() { return DOCKER_TMP_FOLDER + "/input"; }
    public String getDockerOutput() { return DOCKER_TMP_FOLDER + "/output"; }
    public String getDockerIexecOut() { return getDockerOutput() + "/iexec_out"; }
    public String getDockerScone() { return DOCKER_TMP_FOLDER + "/scone"; }

    // buildContainerConfig()

    @Test
    public void shouldBuildContainerConfig() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());

        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(CHAIN_TASK_ID,
                IMAGE_URI, CMD, ENV);

        assertThat(containerConfig.image()).isEqualTo(IMAGE_URI);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);

        String inputBind = containerConfig.hostConfig().binds().get(0);
        String outputBind = containerConfig.hostConfig().binds().get(1);

        assertThat(inputBind).isEqualTo(getDockerInput() + ":/iexec_in");
        assertThat(outputBind).isEqualTo(getDockerIexecOut() + ":/iexec_out");
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutImage() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());

        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(CHAIN_TASK_ID,
                "", CMD, ENV);

        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutHostConfig() {
        // this causes hostConfig to be null
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn("");
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn("");

        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(CHAIN_TASK_ID,
                IMAGE_URI, CMD, ENV);

        assertThat(containerConfig).isNull();
    }

    // buildSconeContainerConfig()

    @Test
    public void shouldBuildSconeContainerConfig() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());
        when(workerConfigurationService.getTaskSconeDir(CHAIN_TASK_ID))
                .thenReturn(getDockerScone());

        ContainerConfig containerConfig = customDockerClient.buildSconeContainerConfig(CHAIN_TASK_ID,
                IMAGE_URI, CMD, ENV);

        assertThat(containerConfig.image()).isEqualTo(IMAGE_URI);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
        assertThat(containerConfig.env()).isEqualTo(ENV);

        Device sgxDevice = containerConfig.hostConfig().devices().get(0);
        assertThat(sgxDevice.pathOnHost()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.pathInContainer()).isEqualTo(SGX_DEVICE_PATH);
        assertThat(sgxDevice.cgroupPermissions()).isEqualTo(SGX_DEVICE_PERMISSIONS);
    }

    @Test
    public void shouldNotBuildSconeContainerConfigWithoutImage() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());
        when(workerConfigurationService.getTaskSconeDir(CHAIN_TASK_ID))
                .thenReturn(getDockerScone());

        ContainerConfig containerConfig = customDockerClient.buildSconeContainerConfig(CHAIN_TASK_ID,
                "", CMD, ENV);

        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldNotBuildSconeContainerConfigWithoutHostConfig() {
        // this causes hostConfig to be null
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn("");
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn("");
        when(workerConfigurationService.getTaskSconeDir(CHAIN_TASK_ID)).thenReturn("");

        ContainerConfig containerConfig = customDockerClient.buildSconeContainerConfig(CHAIN_TASK_ID,
                IMAGE_URI, CMD, ENV);

        assertThat(containerConfig).isNull();
    }

    // pullImage()

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
        boolean imagePulled = customDockerClient.pullImage(CHAIN_TASK_ID, HELLO_WORLD);
        assertThat(imagePulled).isTrue();
    }

    // isImagePulled()

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

    // dockerRun()

    @Test
    public void shouldRunContainerAndGetLogs() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());
        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(CHAIN_TASK_ID,
                HELLO_WORLD, "", ENV);

        String stdout = customDockerClient.dockerRun(CHAIN_TASK_ID, containerConfig, MAX_EXECUTION_TIME);

        assertThat(stdout).contains("Hello from Docker!");
        assertThat(stdout).contains("This message shows that your installation appears to be working correctly");
    }

    @Test
    public void shouldStartContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        customDockerClient.waitContainer(CHAIN_TASK_ID, maxExecutionTime);
        customDockerClient.removeContainer(CHAIN_TASK_ID);
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(getDockerInput());
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(getDockerIexecOut());
        ContainerConfig containerConfig = customDockerClient.buildContainerConfig(CHAIN_TASK_ID,
                HELLO_WORLD, "sleep 30", ENV);

        String stdout = customDockerClient.dockerRun(CHAIN_TASK_ID, containerConfig, MAX_EXECUTION_TIME);

        assertThat(stdout).contains("Hello from Docker!");
        assertThat(stdout).contains("This message shows that your installation appears to be working correctly");



        String containerId = customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        maxExecutionTime = new Date(1000);//1 sec
        customDockerClient.waitContainer(CHAIN_TASK_ID, maxExecutionTime);
        customDockerClient.removeContainer(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotStartContainerWithoutImage() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);
        assertThat(containerId).isEmpty();
    }

    @Test
    public void shouldWaitForContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);
        boolean executionDone = customDockerClient.waitContainer(CHAIN_TASK_ID, maxExecutionTime);
        assertThat(executionDone).isTrue();
        customDockerClient.removeContainer(CHAIN_TASK_ID);
    }

    @Test
    public void shouldWaitForCatagoryTimeout() {

    }

    @Test
    public void shouldRemoveStoppedContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);
        customDockerClient.waitContainer(CHAIN_TASK_ID, maxExecutionTime);

        boolean containerRemoved = customDockerClient.removeContainer(CHAIN_TASK_ID);
        assertThat(containerRemoved).isTrue();
    }

    @Test
    public void shouldNotRemoveRunningContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "ac", "/tmp/worker-test");
        customDockerClient.startContainer(CHAIN_TASK_ID, containerConfig);

        boolean containerRemoved = customDockerClient.removeContainer(CHAIN_TASK_ID);
        assertThat(containerRemoved).isFalse();
        customDockerClient.waitContainer(CHAIN_TASK_ID, maxExecutionTime);
        customDockerClient.removeContainer(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotGetLogsOfEmptyTaskId() {
        String dockerLogs = customDockerClient.getContainerLogs("");
        assertThat(dockerLogs).isEqualTo("Failed to get logs of computation");
    }

    @Test
    public void shouldGetLogsOfBadTaskId() {
        String dockerLogs = customDockerClient.getContainerLogs(CHAIN_TASK_ID);
        assertThat(dockerLogs).isEqualTo("Failed to get logs of computation");
    }


}
