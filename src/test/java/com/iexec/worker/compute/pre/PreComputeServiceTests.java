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
import com.iexec.common.sgx.SgxDriverMode;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.common.tee.TeeEnclaveConfigurationValidator;
import com.iexec.worker.compute.TeeWorkflowConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeConfiguration;
import com.iexec.worker.tee.scone.TeeSconeService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.util.unit.DataSize;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class PreComputeServiceTests {

    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final long PRE_COMPUTE_HEAP = 1024;
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private final String chainTaskId = "chainTaskId";
    private final String datasetUri = "datasetUri";
    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(chainTaskId)
            .datasetAddress("datasetAddress")
            .datasetUri(datasetUri)
            .datasetName("datasetName")
            .datasetChecksum("datasetChecksum")
            .teePostComputeImage("teePostComputeImage")
            .appEnclaveConfiguration(TeeEnclaveConfiguration.builder()
                    .fingerprint("01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b")
                    .heapSize(1024)
                    .entrypoint("python /app/app.py")
                    .build())
            .build();
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder().build();

    @InjectMocks
    private PreComputeService preComputeService;
    @Mock
    private SmsService smsService;
    @Mock
    private DataService dataService;
    @Mock
    private DockerService dockerService;
    @Mock
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private TeeWorkflowConfiguration teeWorkflowConfig;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;
    @Mock
    private SgxService sgxService;
    @Captor
    private ArgumentCaptor<DockerRunRequest> captor;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
    }

    /**
     * Tee pre compute
     */

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenDatasetAndInputFilesArePresent() {
        taskDescription.setInputFiles(List.of("input-file1"));

        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSessionId, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyDatasetIsPresent() {
        // taskDescription.setInputFiles(List.of("input-file1")); <--

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSessionId, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isFalse();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }


    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyInputFilesArePresent() {
        taskDescription.setDatasetAddress("");
        taskDescription.setInputFiles(List.of("input-file1"));

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSessionId, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .isSuccessful(true)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isFalse();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceInvalidEnclaveConfiguration() {
        TeeEnclaveConfiguration enclaveConfig = mock(TeeEnclaveConfiguration.class);
        taskDescription.setAppEnclaveConfiguration(enclaveConfig);
        TeeEnclaveConfigurationValidator validator = mock(TeeEnclaveConfigurationValidator.class);
        when(enclaveConfig.getValidator()).thenReturn(validator);
        when(validator.isValid()).thenReturn(false);
        when(validator.validate()).thenReturn(Collections.singletonList("validation error"));

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService, never()).createTeeSession(workerpoolAuthorization);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTooHighComputeHeapSize() {
        taskDescription.getAppEnclaveConfiguration().setHeapSize(DataSize.ofGigabytes(8).toBytes() + 1);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService, never()).createTeeSession(workerpoolAuthorization);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceCantCreateTeeSession() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn("");

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(smsService).createTeeSession(workerpoolAuthorization);
        verify(teeSconeService, never()).buildPreComputeDockerEnv(anyString(), anyLong());
    }

    @Test
    void shouldNotRunTeePreComputeSinceDockerImageNotFoundLocally() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn("secureSessionId");
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(false);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(dockerService, never()).run(any());
    }

    @Test
    void shouldFailToRunTeePreComputeSinceDockerRunFailed() {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn("secureSessionId");
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn("bind");
        when(workerConfigService.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(70)
                .isSuccessful(false)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
        verify(dockerService).run(any());
    }
}