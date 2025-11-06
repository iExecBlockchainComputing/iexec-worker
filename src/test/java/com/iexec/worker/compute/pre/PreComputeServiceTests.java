/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.ComputeStage;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import com.iexec.worker.workflow.WorkflowError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreComputeServiceTests {

    private static final String IEXEC_IN_BIND = "/path:/iexec_in";
    private static final String PRE_COMPUTE_IMAGE = "preComputeImage";
    private static final String PRE_COMPUTE_ENTRYPOINT = "preComputeEntrypoint";
    private final String chainTaskId = "chainTaskId";
    private final String datasetUri = "datasetUri";
    private final String network = "network";
    private final TaskDescription.TaskDescriptionBuilder taskDescriptionBuilder = TaskDescription.builder()
            .chainTaskId(chainTaskId)
            .datasetAddress("datasetAddress")
            .datasetUri(datasetUri)
            .datasetChecksum("datasetChecksum")
            .teeFramework(TeeFramework.SCONE);
    private final TeeAppProperties preComputeProperties = TeeAppProperties.builder()
            .image(PRE_COMPUTE_IMAGE)
            .entrypoint(PRE_COMPUTE_ENTRYPOINT)
            .build();

    @InjectMocks
    private PreComputeService preComputeService;
    @Mock
    private DockerService dockerService;
    @Mock
    private TeeServicesManager teeServicesManager;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private TeeServicesProperties properties;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;
    @Mock
    private ComputeExitCauseService computeExitCauseService;
    @Mock
    private TeeServicesPropertiesService teeServicesPropertiesService;
    @Mock
    private ComputeDurationsService preComputeDurationsService;
    @Captor
    private ArgumentCaptor<DockerRunRequest> captor;

    @Mock
    private TeeService teeMockedService;


    //region runTeePreCompute
    void prepareMockWhenPreComputeShouldRunForTask(final TaskDescription taskDescription) {
        prepareMocksForPreCompute(
                taskDescription,
                DockerRunResponse.builder()
                        .containerExitCode(0)
                        .finalStatus(DockerRunFinalStatus.SUCCESS)
                        .executionDuration(Duration.ofSeconds(10))
                        .build()
        );
    }

    void prepareMocksForPreCompute(final TaskDescription taskDescription, DockerRunResponse dockerRunResponse) {
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(teeMockedService.buildPreComputeDockerEnv(taskDescription))
                .thenReturn(List.of("env"));
        when(dockerService.getInputBind(chainTaskId)).thenReturn(IEXEC_IN_BIND);
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(dockerRunResponse);
    }

    void verifyDockerRun() {
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        assertThat(capturedRequest.getHostConfig().getNetworkMode()).isEqualTo(network);
        assertThat(capturedRequest.getHostConfig().getBinds()[0]).hasToString(IEXEC_IN_BIND + ":rw");
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenDatasetAndInputFilesArePresent() {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(List.of("input-file1"))
                .build();
        final TaskDescription taskDescription = taskDescriptionBuilder.dealParams(dealParams).build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isTrue();
        assertThat(taskDescription.containsInputFiles()).isTrue();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription))
                .isEqualTo(PreComputeResponse.builder().build());
        verifyDockerRun();
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyDatasetIsPresent() {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isTrue();
        assertThat(taskDescription.containsInputFiles()).isFalse();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription))
                .isEqualTo(PreComputeResponse.builder().build());
        verifyDockerRun();
    }


    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyInputFilesArePresent() {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(List.of("input-file1"))
                .build();
        final TaskDescription taskDescription = taskDescriptionBuilder
                .datasetAddress("")
                .dealParams(dealParams)
                .build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isFalse();
        assertThat(taskDescription.containsInputFiles()).isTrue();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription))
                .isEqualTo(PreComputeResponse.builder().build());
        verifyDockerRun();
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenBulkProcessingRequested() {
        final DealParams dealParams = DealParams.builder()
                .bulkCid("bulk_cid")
                .build();
        final TaskDescription taskDescription = taskDescriptionBuilder
                .datasetAddress("")
                .dealParams(dealParams)
                .build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isFalse();
        assertThat(taskDescription.containsInputFiles()).isFalse();
        assertThat(taskDescription.isBulkRequest()).isTrue();
        assertThat(preComputeService.runTeePreCompute(taskDescription))
                .isEqualTo(PreComputeResponse.builder().build());
        verifyDockerRun();
    }

    @Test
    void shouldNotRunTeePreComputeSinceDockerImageNotFoundLocally() {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(false);

        final PreComputeResponse preComputeResponse = preComputeService.runTeePreCompute(taskDescription);
        assertThat(preComputeResponse.isSuccessful()).isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_IMAGE_MISSING));
        verify(dockerService, never()).run(any());
    }

    @ParameterizedTest
    @MethodSource("shouldFailToRunTeePreComputeSinceDockerRunFailedArgs")
    void shouldFailToRunTeePreComputeSinceDockerRunFailed(Map.Entry<Integer, WorkflowError> exitCodeKeyToExpectedCauseValue) {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder()
                .containerExitCode(exitCodeKeyToExpectedCauseValue.getKey())
                .finalStatus(DockerRunFinalStatus.FAILED)
                .build();
        prepareMocksForPreCompute(taskDescription, dockerRunResponse);
        // Only stub computeExitCauseService for exitCode == 1
        if (exitCodeKeyToExpectedCauseValue.getKey() == 1) {
            when(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(chainTaskId, ComputeStage.PRE, new WorkflowError(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE)))
                    .thenReturn(List.of(exitCodeKeyToExpectedCauseValue.getValue()));
        }

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription);

        assertThat(preComputeResponse.isSuccessful()).isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(exitCodeKeyToExpectedCauseValue.getValue());
        verify(dockerService).run(any());
    }


    private static Stream<Map.Entry<Integer, WorkflowError>> shouldFailToRunTeePreComputeSinceDockerRunFailedArgs() {
        return Map.of(
                1, new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING),
                2, new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_EXIT_REPORTING_FAILED),
                3, new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_TASK_ID_MISSING)
        ).entrySet().stream();
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTimeout() {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.TIMEOUT)
                .build();
        prepareMocksForPreCompute(taskDescription, dockerRunResponse);

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription);

        assertThat(preComputeResponse.isSuccessful()).isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_TIMEOUT));
        verify(dockerService).run(any());
    }

    @Test
    void shouldNotRunPreComputeWhenNotRequired() {
        final TaskDescription taskDescription = taskDescriptionBuilder
                .datasetAddress(BytesUtils.EMPTY_ADDRESS)
                .dealParams(DealParams.builder().build())
                .build();

        assertThat(taskDescription.containsDataset()).isFalse();
        assertThat(taskDescription.containsInputFiles()).isFalse();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription))
                .isEqualTo(PreComputeResponse.builder().build());
    }
    //endregion

    // region getExitCauses
    @ParameterizedTest
    @ValueSource(ints = {4, 5, 10, 42, 127, 255})
    void shouldReturnUnknownIssueForUnmappedExitCodes(int exitCode) {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        prepareMockWhenPreComputeShouldRunForTask(taskDescription);
        final DockerRunResponse dockerResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.FAILED)
                .containerExitCode(exitCode)
                .build();
        when(dockerService.run(any())).thenReturn(dockerResponse);
        final PreComputeResponse response = preComputeService.runTeePreCompute(taskDescription);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getExitCauses())
                .containsExactly(new WorkflowError(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE));
    }
    // endregion
}
