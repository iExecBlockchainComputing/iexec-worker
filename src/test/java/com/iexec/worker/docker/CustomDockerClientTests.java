package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CustomDockerClientTests {

    @Mock private DefaultDockerClient docker;
    @Mock private WorkerConfigurationService workerConfigurationService;

    @InjectMocks
    private CustomDockerClient customDockerClient;

    private DockerClient baseDockerClient;
    private Date maxExecutionTime;

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String IMAGE_URI = "image:tag";
    private static final String CMD = "cmd";
    private static final List<String> env = Arrays.asList("FOO=bar");

    private static final String TEE_ENCLAVE_CHALLENGE = "enclaveChallenge";
    // private static final String NO_TEE_ENCLAVE_CHALLENGE = BytesUtils.EMPTY_ADDRESS;

    @Before
    public void beforeEach() throws DockerCertificateException, DockerException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        baseDockerClient = DefaultDockerClient.fromEnv().build();
        // baseDockerClient.pull("iexechub/vanityeth:latest");
        maxExecutionTime = new Date(30 * 1000);
    }

    @Test
    public void shouldBuildContainerConfig() {
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig(CHAIN_TASK_ID, IMAGE_URI, CMD, env);
        assertThat(containerConfig.image()).isEqualTo(IMAGE_URI);
        assertThat(containerConfig.cmd().get(0)).isEqualTo(CMD);
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutImage() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");

        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("", "cmd", "/tmp/worker-test");
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldNotBuildContainerConfigWithoutHostConfig() {
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("", "cmd", null);
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldPullImage() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "alpine:latest");
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageWithWrongTag() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "alpine:blabla");
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithoutImageName() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "");
        assertThat(imagePulled).isFalse();
    }

    // shouldn't we refuse apps without tags ??
    @Ignore
    @Test
    public void shouldPullLatestImageWithoutTag() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "hello-world");
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldStartContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "aceace", "/tmp/worker-test");//long computation
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        maxExecutionTime = new Date(1000);//1 sec
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
    }

    @Test
    public void shouldNotStartContainerWithoutImage() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isEmpty();
    }

    @Test
    public void shouldWaitForContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        customDockerClient.startContainer("taskId", containerConfig);
        boolean executionDone = customDockerClient.waitContainer("taskId", maxExecutionTime);
        assertThat(executionDone).isTrue();
        customDockerClient.removeContainer("taskId");
    }

    @Test
    public void shouldWaitForCatagoryTimeout() {

    }

    @Test
    public void shouldRemoveStoppedContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        customDockerClient.startContainer("taskId", containerConfig);
        customDockerClient.waitContainer("taskId", maxExecutionTime);

        boolean containerRemoved = customDockerClient.removeContainer("taskId");
        assertThat(containerRemoved).isTrue();
    }

    @Test
    public void shouldNotRemoveRunningContainer() {
        when(workerConfigurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = customDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "ac", "/tmp/worker-test");
        customDockerClient.startContainer("taskId", containerConfig);

        boolean containerRemoved = customDockerClient.removeContainer("taskId");
        assertThat(containerRemoved).isFalse();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
    }

    @Test
    public void shouldNotGetLogsOfEmptyTaskId() {
        String dockerLogs = customDockerClient.getContainerLogs("");
        assertThat(dockerLogs).isEqualTo("Failed to get logs of computation");
    }

    @Test
    public void shouldGetLogsOfBadTaskId() {
        String dockerLogs = customDockerClient.getContainerLogs("taskId");
        assertThat(dockerLogs).isEqualTo("Failed to get logs of computation");
    }


}
