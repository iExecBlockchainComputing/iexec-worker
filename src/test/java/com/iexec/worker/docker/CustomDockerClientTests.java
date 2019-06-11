package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CustomDockerClientTests {


    @Mock
    private WorkerConfigurationService configurationService;

    @InjectMocks
    private CustomDockerClient customDockerClient;

    private DockerClient baseDockerClient;
    private Date maxExecutionTime;

    @Before
    public void beforeEach() throws DockerCertificateException, DockerException, InterruptedException {
        MockitoAnnotations.initMocks(this);
        baseDockerClient = DefaultDockerClient.fromEnv().build();
        baseDockerClient.pull("iexechub/vanityeth:latest");
        maxExecutionTime = new Date(30 * 1000);
    }

    @Test
    public void shouldPullImage() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "iexechub/vanityeth:latest");
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldNotPullImageWithWrongTag() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "iexechub/vanityeth:blabla");
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldNotPullImageWithoutImageName() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "");
        assertThat(imagePulled).isFalse();
    }

    @Test
    public void shouldPullLatestImageWithoutTag() {
        boolean imagePulled = customDockerClient.pullImage("taskId", "iexechub/vanityeth");
        assertThat(imagePulled).isTrue();
    }

    @Test
    public void shouldCreateContainerConfig() {
        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("image:tag", "cmd", "/tmp/worker-test");
        assertThat(containerConfig.image()).isEqualTo("image:tag");
        assertThat(containerConfig.cmd().get(0)).isEqualTo("cmd");
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutImage() {
        when(configurationService.getWorkerName()).thenReturn("worker1");

        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("", "cmd", "/tmp/worker-test");
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutHostConfig() {
        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("", "cmd", null);
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldStartContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
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
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("", "a", "/tmp/worker-test");
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isEmpty();
    }

    @Test
    public void shouldWaitForContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
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
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
                .buildContainerConfig("iexechub/vanityeth:latest", "a", "/tmp/worker-test");
        customDockerClient.startContainer("taskId", containerConfig);
        customDockerClient.waitContainer("taskId", maxExecutionTime);

        boolean containerRemoved = customDockerClient.removeContainer("taskId");
        assertThat(containerRemoved).isTrue();
    }

    @Test
    public void shouldNotRemoveRunningContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        ContainerConfig containerConfig = CustomDockerClient
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
