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

package com.iexec.worker.compute.pre;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.precompute.PreComputeConfig;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeLasConfiguration;
import com.iexec.worker.tee.scone.SconeTeeService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class PreComputeServiceTests {

    private static final String preComputeHeapSize = "preComputeHeapSize";
    private static final String preComputeImageUri = "preComputeImageUri";
    private final String chainTaskId = "chainTaskId";
    private final String datasetUri = "datasetUri";
    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(chainTaskId)
            .datasetAddress("datasetAddress")
            .datasetUri(datasetUri)
            .datasetName("datasetName")
            .datasetChecksum("datasetChecksum")
            .teePostComputeImage("teePostComputeImage")
            .build();
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder().build();
    private static final PreComputeConfig preComputeConfig = new PreComputeConfig(preComputeImageUri,
            "preComputeFingerPrint",
            preComputeHeapSize);

    @InjectMocks
    private PreComputeService preComputeService;
    @Mock
    private SmsService smsService;
    @Mock
    private DataService dataService;
    @Mock
    private DockerService dockerService;
    @Mock
    private SconeTeeService sconeTeeService;
    @Mock
    private SconeLasConfiguration sconeLasConfiguration;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;
    @Captor
    private ArgumentCaptor<DockerRunRequest> captor;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
    }

    /**
     * Standard pre compute
     */

    // Standard pre compute without secret
    @Test
    public void shouldRunStandardPreCompute() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(false);

        Assertions.assertThat(preComputeService.runStandardPreCompute(taskDescription)).isTrue();
        verify(dataService, times(0)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldRunStandardPreComputeWithDatasetDecryption() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(true);

        Assertions.assertThat(preComputeService.runStandardPreCompute(taskDescription)).isTrue();
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldNotRunStandardPreComputeWithDatasetDecryptionSinceCantDecrypt() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(false);

        Assertions.assertThat(preComputeService.runStandardPreCompute(taskDescription)).isFalse();
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    /**
     * Tee pre compute
     */

    @Test
    public void shouldRunTeePreComputeAndPrepareInputDataWhenDatasetAndInputFilesArePresent() {
        taskDescription.setInputFiles(List.of("input-file1"));

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(smsService.getPreComputeConfiguration()).thenReturn(preComputeConfig);
        when(dockerClientInstanceMock.pullImage(preComputeImageUri)).thenReturn(true);
        when(sconeTeeService.buildPreComputeDockerEnv(secureSessionId, preComputeHeapSize))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(sconeLasConfiguration.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(preComputeImageUri);
        Assertions.assertThat(capturedRequest.isSgx()).isTrue();
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    public void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyDatasetIsPresent() {
        // taskDescription.setInputFiles(List.of("input-file1")); <--

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(smsService.getPreComputeConfiguration()).thenReturn(preComputeConfig);
        when(dockerClientInstanceMock.pullImage(preComputeImageUri)).thenReturn(true);
        when(sconeTeeService.buildPreComputeDockerEnv(secureSessionId, preComputeHeapSize))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(sconeLasConfiguration.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isFalse();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(preComputeImageUri);
        Assertions.assertThat(capturedRequest.isSgx()).isTrue();
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }


    @Test
    public void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyInputFilesArePresent() {
        taskDescription.setDatasetAddress("");
        taskDescription.setInputFiles(List.of("input-file1"));

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(smsService.getPreComputeConfiguration()).thenReturn(preComputeConfig);
        when(dockerClientInstanceMock.pullImage(preComputeImageUri)).thenReturn(true);
        when(sconeTeeService.buildPreComputeDockerEnv(secureSessionId, preComputeHeapSize))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(sconeLasConfiguration.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());

        Assertions.assertThat(taskDescription.containsDataset()).isFalse();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(preComputeImageUri);
        Assertions.assertThat(capturedRequest.isSgx()).isTrue();
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    public void shouldFailToRunTeePreComputeSinceCantPullPostComputeImage() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(false);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService, never()).createTeeSession(workerpoolAuthorization);
    }

    @Test
    public void shouldFailToRunTeePreComputeSinceCantCreateTeeSession() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn("");

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService).createTeeSession(workerpoolAuthorization);
        verify(smsService, never()).getPreComputeConfiguration();
    }

    @Test
    public void shouldFailToRunTeePreComputeSinceCantGetPreComputeImageUri() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn("secureSessionId");
        when(smsService.getPreComputeConfiguration()).thenReturn(null);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService).getPreComputeConfiguration();
        verify(dockerClientInstanceMock, never()).pullImage(preComputeImageUri);
    }

    @Test
    public void shouldFailToRunTeePreComputeSinceCantPullPreComputeImage() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn("secureSessionId");
        when(smsService.getPreComputeConfiguration()).thenReturn(preComputeConfig);
        when(dockerClientInstanceMock.pullImage(preComputeImageUri)).thenReturn(false);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(dockerClientInstanceMock).pullImage(preComputeImageUri);
        verify(dockerService, never()).run(any());
    }

    @Test
    public void shouldFailToRunTeePreComputeSinceDockerRunFailed() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn("secureSessionId");
        when(smsService.getPreComputeConfiguration()).thenReturn(preComputeConfig);
        when(dockerClientInstanceMock.pullImage(preComputeImageUri)).thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn("bind");
        when(sconeLasConfiguration.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(70)
                .isSuccessful(false)
                .build());

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(dockerService).run(any());
    }
}