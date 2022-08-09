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
import com.iexec.common.docker.DockerRunFinalStatus;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.sgx.SgxDriverMode;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.common.tee.TeeEnclaveConfigurationValidator;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.TeeWorkflowConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import com.iexec.worker.tee.scone.SconeConfiguration;
import com.iexec.worker.tee.scone.TeeSconeService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.springframework.util.unit.DataSize;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.sms.api.TeeSessionGenerationError.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PreComputeServiceTests {

    private static final String SECURE_SESSION_ID = "secureSessionId";
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
    private final static TeeSessionGenerationResponse secureSession = mock(TeeSessionGenerationResponse.class);


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
    @Mock
    private ComputeExitCauseService computeExitCauseService;
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
    void shouldRunTeePreComputeAndPrepareInputDataWhenDatasetAndInputFilesArePresent() throws TeeSessionGenerationException {
        taskDescription.setInputFiles(List.of("input-file1"));

        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSession, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyDatasetIsPresent() throws TeeSessionGenerationException {
        // taskDescription.setInputFiles(List.of("input-file1")); <--

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSession, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isTrue();
        Assertions.assertThat(taskDescription.containsInputFiles()).isFalse();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }


    @Test
    void shouldRunTeePreComputeAndPrepareInputDataWhenOnlyInputFilesArePresent() throws TeeSessionGenerationException {
        taskDescription.setDatasetAddress("");
        taskDescription.setInputFiles(List.of("input-file1"));

        when(dockerClientInstanceMock.pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.buildPreComputeDockerEnv(secureSession, PRE_COMPUTE_HEAP))
                .thenReturn(List.of("env"));
        String iexecInBind = "/path:/iexec_in";
        when(dockerService.getInputBind(chainTaskId)).thenReturn(iexecInBind);
        String network = "network";
        when(workerConfigService.getDockerNetworkName()).thenReturn(network);
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(0)
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        Assertions.assertThat(taskDescription.containsDataset()).isFalse();
        Assertions.assertThat(taskDescription.containsInputFiles()).isTrue();        
        Assertions.assertThat(preComputeService
                .runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(PreComputeResponse.builder().secureSession(secureSession).build());
        verify(dockerService).run(captor.capture());
        DockerRunRequest capturedRequest = captor.getValue();
        Assertions.assertThat(capturedRequest.getImageUri()).isEqualTo(PRE_COMPUTE_IMAGE);
        Assertions.assertThat(capturedRequest.getEntrypoint()).isEqualTo(PRE_COMPUTE_ENTRYPOINT);
        Assertions.assertThat(capturedRequest.getSgxDriverMode()).isEqualTo(SgxDriverMode.LEGACY);
        Assertions.assertThat(capturedRequest.getDockerNetwork()).isEqualTo(network);
        Assertions.assertThat(capturedRequest.getBinds().get(0)).isEqualTo(iexecInBind);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceInvalidEnclaveConfiguration() throws TeeSessionGenerationException {
        TeeEnclaveConfiguration enclaveConfig = mock(TeeEnclaveConfiguration.class);
        taskDescription.setAppEnclaveConfiguration(enclaveConfig);
        TeeEnclaveConfigurationValidator validator = mock(TeeEnclaveConfigurationValidator.class);
        when(enclaveConfig.getValidator()).thenReturn(validator);
        when(validator.isValid()).thenReturn(false);
        when(validator.validate()).thenReturn(Collections.singletonList("validation error"));

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization).isSuccessful())
                .isFalse();
        verify(smsService, never()).createTeeSession(workerpoolAuthorization);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceTooHighComputeHeapSize() throws TeeSessionGenerationException {
        taskDescription.getAppEnclaveConfiguration().setHeapSize(DataSize.ofGigabytes(8).toBytes() + 1);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization).isSuccessful())
                .isFalse();
        verify(smsService, never()).createTeeSession(workerpoolAuthorization);
    }

    @Test
    void shouldFailToRunTeePreComputeSinceCantCreateTeeSession() throws TeeSessionGenerationException {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSession);

        Assertions.assertThat(preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization).isSuccessful())
                .isFalse();
        verify(smsService).createTeeSession(workerpoolAuthorization);
        verify(teeSconeService, never()).buildPreComputeDockerEnv(any(), anyLong());
    }

    @Test
    void shouldNotRunTeePreComputeSinceDockerImageNotFoundLocally() throws TeeSessionGenerationException {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(false);

        final PreComputeResponse preComputeResponse = preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(preComputeResponse.getExitCause()).isEqualTo(ReplicateStatusCause.PRE_COMPUTE_IMAGE_MISSING);
        verify(dockerService, never()).run(any());
    }

    @ParameterizedTest
    @MethodSource("shouldFailToRunTeePreComputeSinceDockerRunFailedArgs")
    void shouldFailToRunTeePreComputeSinceDockerRunFailed(Map.Entry<Integer, ReplicateStatusCause> exitCodeKeyToExpectedCauseValue) throws TeeSessionGenerationException {
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn("bind");
        when(workerConfigService.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .containerExitCode(exitCodeKeyToExpectedCauseValue.getKey())
                .finalStatus(DockerRunFinalStatus.FAILED)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
        when(computeExitCauseService.getPreComputeExitCauseAndPrune(chainTaskId))
                .thenReturn(exitCodeKeyToExpectedCauseValue.getValue());

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);

        Assertions.assertThat(preComputeResponse.isSuccessful())
                .isFalse();
        Assertions.assertThat(preComputeResponse.getExitCause())
                .isEqualTo(exitCodeKeyToExpectedCauseValue.getValue());
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
        when(dockerClientInstanceMock
                .pullImage(taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization))
                .thenReturn(secureSession);
        when(teeWorkflowConfig.getPreComputeImage()).thenReturn(PRE_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPreComputeHeapSize()).thenReturn(PRE_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPreComputeEntrypoint()).thenReturn(PRE_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(PRE_COMPUTE_IMAGE))
                .thenReturn(true);
        when(dockerService.getInputBind(chainTaskId)).thenReturn("bind");
        when(workerConfigService.getDockerNetworkName()).thenReturn("network");
        when(dockerService.run(any())).thenReturn(DockerRunResponse.builder()
                .finalStatus(DockerRunFinalStatus.TIMEOUT)
                .build());
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        PreComputeResponse preComputeResponse =
                preComputeService.runTeePreCompute(taskDescription, workerpoolAuthorization);

        Assertions.assertThat(preComputeResponse.isSuccessful())
                .isFalse();
        Assertions.assertThat(preComputeResponse.getExitCause())
                .isEqualTo(ReplicateStatusCause.PRE_COMPUTE_TIMEOUT);
        verify(dockerService).run(any());
    }

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

                Arguments.of(POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_WORKER_ADDRESS, TEE_SESSION_GENERATION_POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_WORKER_ADDRESS),
                Arguments.of(POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_PUBLIC_ENCLAVE_CHALLENGE, TEE_SESSION_GENERATION_POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_PUBLIC_ENCLAVE_CHALLENGE),
                Arguments.of(POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CHALLENGE, TEE_SESSION_GENERATION_POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CHALLENGE),
                Arguments.of(POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CREDENTIALS, TEE_SESSION_GENERATION_POST_COMPUTE_GET_SIGNATURE_TOKENS_FAILED_EMPTY_TEE_CREDENTIALS),

                // Secure session generation
                Arguments.of(SECURE_SESSION_STORAGE_CALL_FAILED, TEE_SESSION_GENERATION_SECURE_SESSION_STORAGE_CALL_FAILED),
                Arguments.of(SECURE_SESSION_GENERATION_FAILED, TEE_SESSION_GENERATION_SECURE_SESSION_GENERATION_FAILED),
                Arguments.of(SECURE_SESSION_NO_TEE_PROVIDER, TEE_SESSION_GENERATION_SECURE_SESSION_NO_TEE_PROVIDER),
                Arguments.of(SECURE_SESSION_UNKNOWN_TEE_PROVIDER, TEE_SESSION_GENERATION_SECURE_SESSION_UNKNOWN_TEE_PROVIDER),

                // Miscellaneous
                Arguments.of(GET_TASK_DESCRIPTION_FAILED, TEE_SESSION_GENERATION_GET_TASK_DESCRIPTION_FAILED),
                Arguments.of(NO_SESSION_REQUEST, TEE_SESSION_GENERATION_NO_SESSION_REQUEST),
                Arguments.of(NO_TASK_DESCRIPTION, TEE_SESSION_GENERATION_NO_TASK_DESCRIPTION),
                Arguments.of(GET_SESSION_FAILED, TEE_SESSION_GENERATION_GET_SESSION_FAILED),

                Arguments.of(UNKNOWN_ISSUE, TEE_SESSION_GENERATION_UNKNOWN_ISSUE)
        );
    }

    @ParameterizedTest
    @MethodSource("teeSessionGenerationErrorMap")
    void shouldConvertTeeSessionGenerationError(TeeSessionGenerationError error, ReplicateStatusCause expectedCause) {
        Assertions.assertThat(preComputeService.teeSessionGenerationErrorToReplicateStatusCause(error))
                .isEqualTo(expectedCause);
    }

    @Test
    void shouldAllTeeSessionGenerationErrorHaveMatch() {
        for (TeeSessionGenerationError error : TeeSessionGenerationError.values()) {
            Assertions.assertThat(preComputeService.teeSessionGenerationErrorToReplicateStatusCause(error))
                    .isNotNull();
        }
    }
    // endregion
}