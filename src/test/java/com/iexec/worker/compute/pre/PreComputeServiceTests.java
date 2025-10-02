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
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.ComputeStage;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.sms.api.TeeSessionGenerationError.*;
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
            .teeFramework(TeeFramework.SCONE)
            .appEnclaveConfiguration(TeeEnclaveConfiguration.builder()
                    .fingerprint("01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b")
                    .heapSize(1024)
                    .entrypoint("python /app/app.py")
                    .build());
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder().build();
    private static final TeeSessionGenerationResponse secureSession = mock(TeeSessionGenerationResponse.class);


    @InjectMocks
    private PreComputeService preComputeService;
    @Mock
    private SmsService smsService;
    @Mock
    private DockerService dockerService;
    @Mock
    private TeeServicesManager teeServicesManager;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    TeeAppProperties preComputeProperties;
    @Mock
    TeeAppProperties postComputeProperties;
    @Mock
    private TeeServicesProperties properties;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;
    @Mock
    private SgxService sgxService;
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
    void prepareMockWhenPreComputeShouldRunForTask(final TaskDescription taskDescription) throws TeeSessionGenerationException {
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);
        when(preComputeProperties.getImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(preComputeProperties.getEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeMockedService.buildPreComputeDockerEnv(taskDescription, secureSession))
                .thenReturn(List.of("env"));
        when(dockerService.getInputBind(chainTaskId)).thenReturn(IEXEC_IN_BIND);
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .executionDuration(Duration.ofSeconds(10))
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
    }

    void verifyDockerRun() {
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        assertThat(capturedRequest.getHostConfig().getNetworkMode()).isEqualTo(network);
        assertThat(capturedRequest.getHostConfig().getBinds()[0]).hasToString(IEXEC_IN_BIND + ":rw");
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenDatasetAndInputFilesArePresent() throws TeeSessionGenerationException {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(List.of("input-file1"))
                .build();
        final TaskDescription taskDescription = taskDescriptionBuilder.dealParams(dealParams).build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isTrue();
        assertThat(taskDescription.containsInputFiles()).isTrue();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verifyDockerRun();
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyDatasetIsPresent() throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();

        prepareMockWhenPreComputeShouldRunForTask(taskDescription);

        assertThat(taskDescription.containsDataset()).isTrue();
        assertThat(taskDescription.containsInputFiles()).isFalse();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verifyDockerRun();
    }


    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyInputFilesArePresent() throws TeeSessionGenerationException {
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
        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verifyDockerRun();
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenBulkProcessingRequested() throws TeeSessionGenerationException {
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
        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verifyDockerRun();
    }

    @Test
    void shouldFailToRunTeePreComputeSinceMissingEnclaveConfiguration() {
        final TaskDescription taskDescription = taskDescriptionBuilder.appEnclaveConfiguration(null).build();

        final PreComputeResponse response = preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getExitCauses()).containsExactly(PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION);
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceInvalidEnclaveConfiguration() {
        final TeeEnclaveConfiguration enclaveConfig = TeeEnclaveConfiguration.builder().build();
        final TaskDescription taskDescription = taskDescriptionBuilder.appEnclaveConfiguration(enclaveConfig).build();
        assertThat(enclaveConfig.getValidator().isValid()).isFalse();

        final PreComputeResponse response = preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getExitCauses()).containsExactly(PRE_COMPUTE_INVALID_ENCLAVE_CONFIGURATION);
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTooHighComputeHeapSize() {
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        final TeeEnclaveConfiguration enclaveConfiguration = TeeEnclaveConfiguration.builder()
                .fingerprint("01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b")
                .heapSize(DataSize.ofGigabytes(8).toBytes() + 1)
                .entrypoint("python /app/app.py")
                .build();
        final TaskDescription taskDescription = taskDescriptionBuilder.appEnclaveConfiguration(enclaveConfiguration).build();

        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization).isSuccessful())
                .isFalse();
        verifyNoInteractions(smsService);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceCantCreateTeeSession() throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(null);

        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization).isSuccessful())
                .isFalse();
        verify(smsService).createTeeSession(workerpoolAuthorization);
        verify(teeMockedService, never()).buildPreComputeDockerEnv(any(), any());
    }

    @Test
    void shouldNotRunTeePreComputeSinceDockerImageNotFoundLocally() throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(preComputeProperties.getImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(false);

        final PreComputeResponse preComputeResponse = preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);
        assertThat(preComputeResponse.isSuccessful()).isFalse();
        assertThat(preComputeResponse.getExitCauses()).containsExactly(ReplicateStatusCause.PRE_COMPUTE_IMAGE_MISSING);
        verify(dockerService, never()).run(any());
    }

    @ParameterizedTest
    @MethodSource("shouldFailToRunTeePreComputeSinceDockerRunFailedArgs")
    void shouldFailToRunTeePreComputeSinceDockerRunFailed(Map.Entry<Integer, ReplicateStatusCause> exitCodeKeyToExpectedCauseValue) throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(preComputeProperties.getImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(preComputeProperties.getEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn(IEXEC_IN_BIND);
        when(workerConfigService.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(exitCodeKeyToExpectedCauseValue.getKey())
                .finalStatus(DockerRunFinalStatus.FAILED)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
        // Only stub computeExitCauseService for exitCode == 1
        if (exitCodeKeyToExpectedCauseValue.getKey() == 1) {
            when(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(chainTaskId, ComputeStage.PRE, PRE_COMPUTE_FAILED_UNKNOWN_ISSUE))
                    .thenReturn(List.of(exitCodeKeyToExpectedCauseValue.getValue()));
        }

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);

        assertThat(preComputeResponse.isSuccessful())
                .isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(exitCodeKeyToExpectedCauseValue.getValue());
        verify(dockerService).run(any());
    }


    private static Stream<Map.Entry<Integer, ReplicateStatusCause>> shouldFailToRunTeePreComputeSinceDockerRunFailedArgs() {
        return Map.of(
                1, ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                2, ReplicateStatusCause.PRE_COMPUTE_EXIT_REPORTING_FAILED,
                3, ReplicateStatusCause.PRE_COMPUTE_TASK_ID_MISSING
        ).entrySet().stream();
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTimeout() throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(preComputeProperties.getImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(preComputeProperties.getEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn(IEXEC_IN_BIND);
        when(workerConfigService.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.TIMEOUT)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);

        assertThat(preComputeResponse.isSuccessful())
                .isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(ReplicateStatusCause.PRE_COMPUTE_TIMEOUT);
        verify(dockerService).run(any());
    }

    @Test
    void shouldNotRunPreComputeWhenNotRequired() throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder
                .datasetAddress(BytesUtils.EMPTY_ADDRESS)
                .dealParams(DealParams.builder().build())
                .build();

        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);

        assertThat(taskDescription.containsDataset()).isFalse();
        assertThat(taskDescription.containsInputFiles()).isFalse();
        assertThat(taskDescription.isBulkRequest()).isFalse();
        assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
    }
    //endregion

    // region teeSessionGenerationErrorToReplicateStatusCause
    static Stream<Arguments> teeSessionGenerationErrorMap() {
        return Stream.of(
                // Authorization
                Arguments.of(INVALID_AUTHORIZATION, TEE_SESSION_GENERATION_INVALID_AUTHORIZATION),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_EMPTY_PARAMS_UNAUTHORIZED, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_EMPTY_PARAMS_UNAUTHORIZED),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_NO_MATCH_ONCHAIN_TYPE, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_NO_MATCH_ONCHAIN_TYPE),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_GET_CHAIN_TASK_FAILED, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_GET_CHAIN_TASK_FAILED),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_TASK_NOT_ACTIVE, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_TASK_NOT_ACTIVE),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_GET_CHAIN_DEAL_FAILED, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_GET_CHAIN_DEAL_FAILED),
                Arguments.of(EXECUTION_NOT_AUTHORIZED_INVALID_SIGNATURE, TEE_SESSION_GENERATION_EXECUTION_NOT_AUTHORIZED_INVALID_SIGNATURE),

                // Pre-compute
                Arguments.of(PRE_COMPUTE_GET_DATASET_SECRET_FAILED, TEE_SESSION_GENERATION_PRE_COMPUTE_GET_DATASET_SECRET_FAILED),

                // App-compute
                Arguments.of(APP_COMPUTE_NO_ENCLAVE_CONFIG, TEE_SESSION_GENERATION_APP_COMPUTE_NO_ENCLAVE_CONFIG),
                Arguments.of(APP_COMPUTE_INVALID_ENCLAVE_CONFIG, TEE_SESSION_GENERATION_APP_COMPUTE_INVALID_ENCLAVE_CONFIG),

                // Post-compute
                Arguments.of(POST_COMPUTE_GET_ENCRYPTION_TOKENS_FAILED_EMPTY_BENEFICIARY_KEY, TEE_SESSION_GENERATION_POST_COMPUTE_GET_ENCRYPTION_TOKENS_FAILED_EMPTY_BENEFICIARY_KEY),
                Arguments.of(POST_COMPUTE_GET_STORAGE_TOKENS_FAILED, TEE_SESSION_GENERATION_POST_COMPUTE_GET_STORAGE_TOKENS_FAILED),

                Arguments.of(GET_SIGNATURE_TOKENS_FAILED_EMPTY_WORKER_ADDRESS, TEE_SESSION_GENERATION_GET_SIGNATURE_TOKENS_FAILED_EMPTY_WORKER_ADDRESS),
                Arguments.of(GET_SIGNATURE_TOKENS_FAILED_EMPTY_PUBLIC_ENCLAVE_CHALLENGE, TEE_SESSION_GENERATION_GET_SIGNATURE_TOKENS_FAILED_EMPTY_PUBLIC_ENCLAVE_CHALLENGE),
                Arguments.of(GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CHALLENGE, TEE_SESSION_GENERATION_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CHALLENGE),
                Arguments.of(GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CREDENTIALS, TEE_SESSION_GENERATION_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CREDENTIALS),

                // Secure session generation
                Arguments.of(SECURE_SESSION_STORAGE_CALL_FAILED, TEE_SESSION_GENERATION_SECURE_SESSION_STORAGE_CALL_FAILED),
                Arguments.of(SECURE_SESSION_GENERATION_FAILED, TEE_SESSION_GENERATION_SECURE_SESSION_GENERATION_FAILED),
                Arguments.of(SECURE_SESSION_NO_TEE_FRAMEWORK, TEE_SESSION_GENERATION_SECURE_SESSION_NO_TEE_FRAMEWORK),

                // Miscellaneous
                Arguments.of(GET_TASK_DESCRIPTION_FAILED, TEE_SESSION_GENERATION_GET_TASK_DESCRIPTION_FAILED),
                Arguments.of(NO_SESSION_REQUEST, TEE_SESSION_GENERATION_NO_SESSION_REQUEST),
                Arguments.of(NO_TASK_DESCRIPTION, TEE_SESSION_GENERATION_NO_TASK_DESCRIPTION),

                Arguments.of(UNKNOWN_ISSUE, TEE_SESSION_GENERATION_UNKNOWN_ISSUE)
        );
    }

    @ParameterizedTest
    @MethodSource("teeSessionGenerationErrorMap")
    void shouldConvertTeeSessionGenerationError(TeeSessionGenerationError error, ReplicateStatusCause expectedCause) {
        assertThat(preComputeService.teeSessionGenerationErrorToReplicateStatusCause(error))
                .isEqualTo(expectedCause);
    }

    @Test
    void shouldAllTeeSessionGenerationErrorHaveMatch() {
        for (TeeSessionGenerationError error : TeeSessionGenerationError.values()) {
            assertThat(preComputeService.teeSessionGenerationErrorToReplicateStatusCause(error))
                    .isNotNull();
        }
    }
    // endregion

    // region getExitCauses
    @ParameterizedTest
    @ValueSource(ints = {4, 5, 10, 42, 127, 255})
    void shouldReturnUnknownIssueForUnmappedExitCodes(int exitCode) throws TeeSessionGenerationException {
        final TaskDescription taskDescription = taskDescriptionBuilder.build();
        when(workerConfigService.getTeeComputeMaxHeapSizeGb()).thenReturn(8);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeServicesPropertiesService.getTeeServicesProperties(chainTaskId)).thenReturn(properties);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);
        when(preComputeProperties.getImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(preComputeProperties.getEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE)).thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn(IEXEC_IN_BIND);
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
        DockerRunResponse dockerResponse = DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.FAILED)
                .containerExitCode(exitCode)
                .build();
        when(dockerService.run(any())).thenReturn(dockerResponse);
        PreComputeResponse response = preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getExitCauses())
                .hasSize(1)
                .containsExactly(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }
    // endregion
}
