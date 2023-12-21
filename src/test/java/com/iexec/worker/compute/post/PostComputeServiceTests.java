/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.compute.post;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_TOO_LONG_RESULT_FILE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
class PostComputeServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String WORKER_NAME = "WORKER_NAME";
    private final static String TEE_POST_COMPUTE_IMAGE = "TEE_POST_COMPUTE_IMAGE";
    private final static long TEE_POST_COMPUTE_HEAP = 1024;
    private final static String TEE_POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private final static TeeSessionGenerationResponse SECURE_SESSION = mock(TeeSessionGenerationResponse.class);
    private final static long MAX_EXECUTION_TIME = 1000;

    @TempDir
    public File jUnitTemporaryFolder;
    private TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .datasetUri(DATASET_URI)
            .build();
    private String output;
    private String iexecOut;
    private String computedJson;

    @InjectMocks
    private PostComputeService postComputeService;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerService dockerService;
    @Mock
    private TeeServicesManager teeServicesManager;
    @Mock
    private TeeAppProperties preComputeProperties;
    @Mock
    private TeeAppProperties postComputeProperties;
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
    private ComputeDurationsService postComputeDurationsService;

    @Mock
    private TeeService teeMockedService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(properties.getPreComputeProperties()).thenReturn(preComputeProperties);
        when(properties.getPostComputeProperties()).thenReturn(postComputeProperties);
        when(teeServicesPropertiesService.getTeeServicesProperties(CHAIN_TASK_ID)).thenReturn(properties);

        output = jUnitTemporaryFolder.getAbsolutePath();
        iexecOut = output + IexecFileHelper.SLASH_IEXEC_OUT;
        computedJson = iexecOut + IexecFileHelper.SLASH_COMPUTED_JSON;
    }

    //region runStandardPostCompute
    private void logDirectoryTree(String path) {
        log.info("\n{}", FileHelper.printDirectoryTree(new File(path)));
    }

    @Test
    void shouldRunStandardPostCompute() throws IOException {
        assertThat(new File(iexecOut).mkdir()).isTrue();
        assertThat(new File(computedJson).createNewFile()).isTrue();
        logDirectoryTree(output);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);

        assertThat(postComputeService.runStandardPostCompute(taskDescription).isSuccessful()).isTrue();
        logDirectoryTree(output);
        assertThat(new File(output + "/iexec_out.zip")).exists();
        assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON)).exists();
    }

    @Test
    void shouldNotRunStandardPostComputeSinceWrongSourceForZip() throws IOException {
        assertThat(new File(iexecOut).mkdir()).isTrue();
        assertThat(new File(computedJson).createNewFile()).isTrue();
        logDirectoryTree(output);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn("dummyIexecOut");

        assertThat(postComputeService.runStandardPostCompute(taskDescription).isSuccessful()).isFalse();
        logDirectoryTree(output);
    }

    @Test
    void shouldNotRunStandardPostComputeSinceNoComputedFileToCopy() {
        assertThat(new File(iexecOut).mkdir()).isTrue();
        //don't create iexec_out.zip
        logDirectoryTree(output);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);

        assertThat(postComputeService.runStandardPostCompute(taskDescription).isSuccessful()).isFalse();
        logDirectoryTree(output);
        assertThat(new File(output + "/iexec_out.zip")).exists();
        assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON)).doesNotExist();
    }
    //endregion

    // region checkResultFilesName
    @Test
    void shouldPassResultFilesNameCheckWhenNoFile() {
        assertThat(postComputeService.checkResultFilesName(CHAIN_TASK_ID, jUnitTemporaryFolder.getAbsolutePath()))
                .isEmpty();
    }

    @Test
    void shouldPassResultFilesNameCheckWhenCorrectFiles() throws IOException {
        assertTrue(new File(jUnitTemporaryFolder, "result.txt").createNewFile());
        assertTrue(new File(jUnitTemporaryFolder, "computed.json").createNewFile());

        assertThat(postComputeService.checkResultFilesName(CHAIN_TASK_ID, jUnitTemporaryFolder.getAbsolutePath()))
                .isEmpty();
    }

    @Test
    void shouldFailResultFilesNameCheckWhenWrongFolder() {
        assertThat(postComputeService.checkResultFilesName(CHAIN_TASK_ID, "/dummy/folder/that/doesnt/exist"))
                .contains(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    @Test
    void shouldFailResultFilesNameCheckWhenFileNameTooLong() throws IOException {
        assertTrue(new File(jUnitTemporaryFolder, "result-0x0000000000000000000.txt").createNewFile());
        assertTrue(new File(jUnitTemporaryFolder, "computed.json").createNewFile());

        assertThat(postComputeService.checkResultFilesName(CHAIN_TASK_ID, jUnitTemporaryFolder.getAbsolutePath()))
                .contains(POST_COMPUTE_TOO_LONG_RESULT_FILE_NAME);
    }
    // endregion

    //region runTeePostCompute
    @Test
    void shouldRunTeePostComputeAndConnectToLasNetwork() {
        String lasNetworkName = "networkName";
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(postComputeProperties.getImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(postComputeProperties.getEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeMockedService.buildPostComputeDockerEnv(taskDescription, SECURE_SESSION))
                .thenReturn(env);
        String iexecOutBind = iexecOut + ":" + IexecFileHelper.SLASH_IEXEC_OUT;
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn(iexecOutBind);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        when(workerConfigService.getDockerNetworkName()).thenReturn(lasNetworkName);
        DockerRunResponse expectedDockerRunResponse = DockerRunResponse
                .builder()
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .executionDuration(Duration.ofSeconds(10))
                .build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
        List<Device> devices = List.of(Device.parse("/dev/isgx"));
        when(sgxService.getSgxDevices()).thenReturn(devices);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION);

        assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(dockerService, times(1)).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(Bind.parse(iexecOutBind))
                .withDevices(devices)
                .withNetworkMode(lasNetworkName);
        assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .hostConfig(hostConfig)
                        .chainTaskId(CHAIN_TASK_ID)
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID + "-tee-post-compute")
                        .imageUri(TEE_POST_COMPUTE_IMAGE)
                        .entrypoint(TEE_POST_COMPUTE_ENTRYPOINT)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(env)
                        .sgxDriverMode(SgxDriverMode.LEGACY)
                        .build()
        );
    }

    @Test
    void shouldNotRunTeePostComputeSinceDockerImageNotFoundLocally() {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .build();
        when(postComputeProperties.getImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(postComputeProperties.getEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(false);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION);
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCause()).isEqualTo(ReplicateStatusCause.POST_COMPUTE_IMAGE_MISSING);
        verify(dockerService, never()).run(any());
    }

    @ParameterizedTest
    @MethodSource("shouldRunTeePostComputeWithFailDockerResponseArgs")
    void shouldRunTeePostComputeWithFailDockerResponse(Map.Entry<Integer, ReplicateStatusCause> exitCodeKeyToExpectedCauseValue) {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(postComputeProperties.getImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(postComputeProperties.getEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeMockedService.buildPostComputeDockerEnv(taskDescription, SECURE_SESSION))
                .thenReturn(env);
        String iexecOutBind = iexecOut + ":" + IexecFileHelper.SLASH_IEXEC_OUT;
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn(iexecOutBind);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        when(workerConfigService.getDockerNetworkName()).thenReturn("lasNetworkName");
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .finalStatus(DockerRunFinalStatus.FAILED)
                        .containerExitCode(exitCodeKeyToExpectedCauseValue.getKey())
                        .build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);
        when(computeExitCauseService.getPostComputeExitCauseAndPrune(CHAIN_TASK_ID))
                .thenReturn(exitCodeKeyToExpectedCauseValue.getValue());

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION);

        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCause())
                .isEqualTo(exitCodeKeyToExpectedCauseValue.getValue());
        verify(dockerService).run(any());
    }

    private static Stream<Map.Entry<Integer, ReplicateStatusCause>> shouldRunTeePostComputeWithFailDockerResponseArgs() {
        return Map.of(
                1, ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND,
                2, ReplicateStatusCause.POST_COMPUTE_EXIT_REPORTING_FAILED,
                3, ReplicateStatusCause.POST_COMPUTE_TASK_ID_MISSING
        ).entrySet().stream();
    }

    @Test
    void shouldNotRunTeePostComputeSinceTimeout() {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(postComputeProperties.getImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(postComputeProperties.getHeapSizeInBytes()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(postComputeProperties.getEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeMockedService.buildPostComputeDockerEnv(taskDescription, SECURE_SESSION))
                .thenReturn(env);
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn("/iexec_out:/iexec_out");
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        when(workerConfigService.getDockerNetworkName()).thenReturn("lasNetworkName");
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .finalStatus(DockerRunFinalStatus.TIMEOUT)
                        .build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);
        when(sgxService.getSgxDriverMode()).thenReturn(SgxDriverMode.LEGACY);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION);

        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCause())
                .isEqualTo(ReplicateStatusCause.POST_COMPUTE_TIMEOUT);
        verify(dockerService).run(any());
    }
    //endregion
}
