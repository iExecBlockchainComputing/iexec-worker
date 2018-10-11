package com.iexec.worker.docker;

import com.iexec.worker.utils.WorkerConfigurationService;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Volume;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class CustomDockerClientTests {


    @Mock
    private WorkerConfigurationService configurationService;

    @InjectMocks
    private CustomDockerClient customDockerClient;

    private DockerClient baseDockerClient;

    @Before
    public void init() throws DockerCertificateException {
        MockitoAnnotations.initMocks(this);
        baseDockerClient = DefaultDockerClient.fromEnv().build();
    }

    @After
    public void after() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        customDockerClient.removeVolume("taskId");
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
    public void shouldCreateVolumeName() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String taskVolumeName = customDockerClient.getTaskVolumeName("taskId");
        assertThat(taskVolumeName).isEqualTo("iexec-worker-worker1-taskId");
    }

    @Test
    public void shouldNotCreateVolumeName() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String taskVolumeName = customDockerClient.getTaskVolumeName("");
        assertThat(taskVolumeName).isEmpty();
    }

    @Test
    public void shouldNotCreateVolumeNameWithoutWorkerAddress() {
        when(configurationService.getWorkerName()).thenReturn("");
        String taskVolumeName = customDockerClient.getTaskVolumeName("taskId");
        assertThat(taskVolumeName).isEmpty();
    }

    @Test
    public void shouldCreateVolume() throws DockerException, InterruptedException {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        Volume inspectedVolume = baseDockerClient.inspectVolume("iexec-worker-worker1-taskId");
        assertThat(volume.name()).isEqualTo(inspectedVolume.name());
    }

    @Test
    public void shouldNotCreateVolume() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("");
        assertThat(volume).isNull();
    }

    @Test
    public void shouldRemoveVolume() throws DockerException, InterruptedException {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        Volume inspectedVolume = baseDockerClient.inspectVolume("iexec-worker-worker1-taskId");
        assertThat(volume.name()).isEqualTo(inspectedVolume.name());

        boolean isVolumeRemoved = customDockerClient.removeVolume("taskId");
        assertThat(isVolumeRemoved).isTrue();
    }

    @Test
    public void shouldNotRemoveUnexistingVolume() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        boolean isVolumeRemoved = customDockerClient.removeVolume("taskId");
        assertThat(isVolumeRemoved).isFalse();
    }

    @Test
    public void shouldCreateHostConfig() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");

        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        assertThat(hostConfig).isNotNull();
    }

    @Test
    public void shouldNotCreateHostConfig() {
        HostConfig hostConfig = CustomDockerClient.createHostConfig(null);
        assertThat(hostConfig).isNull();
    }

    @Test
    public void shouldCreateContainerConfig() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);

        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("image:tag", "cmd", hostConfig);
        assertThat(containerConfig.image()).isEqualTo("image:tag");
        assertThat(containerConfig.cmd().get(0)).isEqualTo("cmd");
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutImage() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);

        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("", "cmd", hostConfig);
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutHostConfig() {
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("", "cmd", null);
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldStartContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("iexechub/vanityeth:latest", "a", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        customDockerClient.waitContainerForExitStatus("taskId", containerId);
        customDockerClient.removeContainer(containerId);
    }

    @Test
    public void shouldNotStartContainerWithoutImage() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("", "a", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isEmpty();
        customDockerClient.waitContainerForExitStatus("taskId", containerId);
        customDockerClient.removeContainer(containerId);
    }

    @Test
    public void shouldWaitForContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        HostConfig hostConfig = HostConfig.builder().build();
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("hello-world:latest", "", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        boolean executionDone = customDockerClient.waitContainerForExitStatus("taskId", containerId);
        assertThat(executionDone).isTrue();
        customDockerClient.waitContainerForExitStatus("taskId", containerId);
        customDockerClient.removeContainer(containerId);
    }

    @Test
    public void shouldWaitForCatagoryTimeout() {

    }

    @Test
    public void shouldGetContainerArchive() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("iexechub/vanityeth:latest", "a", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        boolean executionDone = customDockerClient.waitContainerForExitStatus("taskId", containerId);
        InputStream containerResultArchive = customDockerClient.getContainerResultArchive(containerId);
        assertThat(containerResultArchive).isNotNull();
        customDockerClient.waitContainerForExitStatus("taskId", containerId);
        customDockerClient.removeContainer(containerId);
    }

    @Test
    public void shouldRemoveStoppedContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("hello-world:latest", "", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        customDockerClient.waitContainerForExitStatus("taskId", containerId);

        boolean containerRemoved = customDockerClient.removeContainer(containerId);
        assertThat(containerRemoved).isTrue();
    }

    @Test
    public void shouldNotRemoveRunningContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        Volume volume = customDockerClient.createVolume("taskId");
        HostConfig hostConfig = CustomDockerClient.createHostConfig(volume);
        ContainerConfig containerConfig = CustomDockerClient
                .createContainerConfig("iexechub/vanityeth:latest", "a", hostConfig);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);

        boolean containerRemoved = customDockerClient.removeContainer(containerId);
        assertThat(containerRemoved).isFalse();
        customDockerClient.waitContainerForExitStatus("taskId", containerId);
        customDockerClient.removeContainer(containerId);
    }

    @Test
    public void shouldNotGetDockerLogsOfEmptyContainerId() {
        String dockerLogs = customDockerClient.getDockerLogs("");
        assertThat(dockerLogs).isEmpty();
    }

    @Test
    public void shouldGetFailedDockerLogs() {
        String dockerLogs = customDockerClient.getDockerLogs("removedContainerId");
        assertThat(dockerLogs).isEqualTo("Failed to get logs of computation");
    }


}
