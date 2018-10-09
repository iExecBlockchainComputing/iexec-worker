package com.iexec.worker;

import com.iexec.worker.docker.DockerService;
import com.iexec.worker.docker.MetadataResult;
import com.iexec.worker.utils.WorkerConfigurationService;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class DockerServiceTests {

    @Mock
    private WorkerConfigurationService configurationService;

    @InjectMocks
    private DockerService dockerService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDockerRunAndZip() throws DockerCertificateException {
        when(configurationService.getResultBaseDir()).thenReturn("/tmp/iexec-worker-result-test");
        dockerService.onPostConstruct();
        MetadataResult metadataResult = dockerService.dockerRun("taskID", "iexechub/vanityeth:latest", "a");
        assertThat(metadataResult).isNotNull();
    }

}
