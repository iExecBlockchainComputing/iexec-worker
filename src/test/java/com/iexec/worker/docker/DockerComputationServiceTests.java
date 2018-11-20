package com.iexec.worker.docker;

import com.iexec.worker.config.WorkerConfigurationService;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DockerComputationServiceTests {

    @Mock
    private WorkerConfigurationService configurationService;

    @InjectMocks
    private DockerComputationService dockerComputationService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDockerRunAndZip() throws DockerCertificateException {
        /*
        when(configurationService.getResultBaseDir()).thenReturn("/tmp/iexec-worker-result-test");
        dockerComputationService.onPostConstruct();
        MetadataResult metadataResult = dockerComputationService.dockerRun("taskID", "iexechub/vanityeth:latest", "a");
        assertThat(metadataResult).isNotNull();
        */
    }

}
