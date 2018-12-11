package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.Volume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
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
        maxExecutionTime = new Date(30*1000);
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
        String taskVolumeName = customDockerClient.getVolumeName("taskId");
        assertThat(taskVolumeName).isEqualTo("iexec-worker-worker1-taskId");
    }

    @Test
    public void shouldNotCreateVolumeName() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String taskVolumeName = customDockerClient.getVolumeName("");
        assertThat(taskVolumeName).isEmpty();
    }

    @Test
    public void shouldNotCreateVolumeNameWithoutWorkerAddress() {
        when(configurationService.getWorkerName()).thenReturn("");
        String taskVolumeName = customDockerClient.getVolumeName("taskId");
        assertThat(taskVolumeName).isEmpty();
    }

    @Test
    public void shouldCreateVolume() throws DockerException, InterruptedException {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        Volume inspectedVolume = baseDockerClient.inspectVolume("iexec-worker-worker1-taskId");
        assertThat(volumeName).isEqualTo(inspectedVolume.name());
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldNotCreateVolume() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("");
        assertThat(volumeName).isEmpty();
    }

    @Test
    public void shouldRemoveVolume() throws DockerException, InterruptedException {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        Volume inspectedVolume = baseDockerClient.inspectVolume("iexec-worker-worker1-taskId");
        assertThat(volumeName).isEqualTo(inspectedVolume.name());

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
    public void shouldCreateContainerConfig() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");

        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("image:tag", "cmd", volumeName);
        assertThat(containerConfig.image()).isEqualTo("image:tag");
        assertThat(containerConfig.cmd().get(0)).isEqualTo("cmd");
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutImage() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");

        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("", "cmd", volumeName);
        assertThat(containerConfig).isNull();
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldNotCreateContainerConfigWithoutHostConfig() {
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("", "cmd", null);
        assertThat(containerConfig).isNull();
    }

    @Test
    public void shouldStartContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "a", volumeName);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldStopComputingIfTooLong() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "aceace", volumeName);//long computation
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isNotNull();
        assertThat(containerId).isNotEmpty();
        maxExecutionTime = new Date(1000);//1 sec
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldNotStartContainerWithoutImage() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("", "a", volumeName);
        String containerId = customDockerClient.startContainer("taskId", containerConfig);
        assertThat(containerId).isEmpty();
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldWaitForContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "a", volumeName);
        customDockerClient.startContainer("taskId", containerConfig);
        boolean executionDone = customDockerClient.waitContainer("taskId", maxExecutionTime);
        assertThat(executionDone).isTrue();
        customDockerClient.removeContainer("taskId");
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldWaitForCatagoryTimeout() {

    }

    @Test
    public void shouldGetContainerArchive() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "a", volumeName);
        customDockerClient.startContainer("taskId", containerConfig);
        InputStream containerResultArchive = customDockerClient.getContainerResultArchive("taskId");
        assertThat(containerResultArchive).isNotNull();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldRemoveStoppedContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "a", volumeName);
        customDockerClient.startContainer("taskId", containerConfig);
        customDockerClient.waitContainer("taskId", maxExecutionTime);

        boolean containerRemoved = customDockerClient.removeContainer("taskId");
        assertThat(containerRemoved).isTrue();
        customDockerClient.removeVolume("taskId");
    }

    @Test
    public void shouldNotRemoveRunningContainer() {
        when(configurationService.getWorkerName()).thenReturn("worker1");
        String volumeName = customDockerClient.createVolume("taskId");
        ContainerConfig containerConfig = CustomDockerClient
                .getContainerConfig("iexechub/vanityeth:latest", "ac", volumeName);
        customDockerClient.startContainer("taskId", containerConfig);

        boolean containerRemoved = customDockerClient.removeContainer("taskId");
        assertThat(containerRemoved).isFalse();
        customDockerClient.waitContainer("taskId", maxExecutionTime);
        customDockerClient.removeContainer("taskId");
        customDockerClient.removeVolume("taskId");
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
