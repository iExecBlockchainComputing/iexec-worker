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

package com.iexec.worker.compute.post;

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.worker.compute.TeeWorkflowConfiguration;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeConfiguration;
import com.iexec.worker.tee.scone.TeeSconeService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PostComputeServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String SCONE_CAS_URL = "SCONE_CAS_URL";
    private final static String WORKER_NAME = "WORKER_NAME";
    private final static String TEE_POST_COMPUTE_IMAGE = "TEE_POST_COMPUTE_IMAGE";
    private final static long TEE_POST_COMPUTE_HEAP = 1024;
    private final static String TEE_POST_COMPUTE_ENTRYPOINT = "postComputeEntrypoint";
    private final static String SECURE_SESSION_ID = "SECURE_SESSION_ID";
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
    private PublicConfigurationService publicConfigService;
    @Mock
    private ResultService resultService;
    @Mock
    private TeeSconeService teeSconeService;
    @Mock
    private SconeConfiguration sconeConfig;
    @Mock
    private TeeWorkflowConfiguration teeWorkflowConfig;
    @Mock
    private DockerClientInstance dockerClientInstanceMock;

    @BeforeEach
    public void beforeEach() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(dockerService.getClient()).thenReturn(dockerClientInstanceMock);
        when(sconeConfig.getCasUrl()).thenReturn(SCONE_CAS_URL);
        output = jUnitTemporaryFolder.getAbsolutePath();
        iexecOut = output + IexecFileHelper.SLASH_IEXEC_OUT;
        computedJson = iexecOut + IexecFileHelper.SLASH_COMPUTED_JSON;
    }

    /**
     * Standard post compute
     */

    @Test
    public void shouldRunStandardPostCompute() throws IOException {
        Assertions.assertThat(new File(iexecOut).mkdir()).isTrue();
        Assertions.assertThat(new File(computedJson).createNewFile()).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);

        Assertions.assertThat(postComputeService.runStandardPostCompute(taskDescription)).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        Assertions.assertThat(new File(output + "/iexec_out.zip")).exists();
        Assertions.assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON)).exists();
        verify(resultService, times(0)).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRunStandardPostComputeSinceWrongSourceForZip() throws IOException {
        Assertions.assertThat(new File(iexecOut).mkdir()).isTrue();
        Assertions.assertThat(new File(computedJson).createNewFile()).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn("dummyIexecOut");

        Assertions.assertThat(postComputeService.runStandardPostCompute(taskDescription)).isFalse();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
    }

    @Test
    public void shouldNotRunStandardPostComputeSinceNoComputedFileToCopy() {
        Assertions.assertThat(new File(iexecOut).mkdir()).isTrue();
        //don't create iexec_out.zip
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);

        Assertions.assertThat(postComputeService.runStandardPostCompute(taskDescription)).isFalse();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        Assertions.assertThat(new File(output + "/iexec_out.zip")).exists();
        Assertions.assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON).exists()).isFalse();
    }

    @Test
    public void shouldRunStandardPostComputeWithResultEncryption() throws IOException {
        taskDescription.setResultEncryption(true);
        Assertions.assertThat(new File(iexecOut).mkdir()).isTrue();
        Assertions.assertThat(new File(computedJson).createNewFile()).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(true);

        Assertions.assertThat(postComputeService.runStandardPostCompute(taskDescription)).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        Assertions.assertThat(new File(output + "/iexec_out.zip")).exists();
        Assertions.assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON)).exists();
        verify(resultService, times(1)).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotRunStandardPostComputeWithResultEncryptionSinceCantEncrypt() throws IOException {
        taskDescription.setResultEncryption(true);
        Assertions.assertThat(new File(iexecOut).mkdir()).isTrue();
        Assertions.assertThat(new File(computedJson).createNewFile()).isTrue();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(false);

        Assertions.assertThat(postComputeService.runStandardPostCompute(taskDescription)).isFalse();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        verify(resultService, times(1)).encryptResult(CHAIN_TASK_ID);
    }

    /**
     * Tee post compute
     */

    @Test
    public void shouldRunTeePostComputeAndConnectToLasNetwork() {
        String lasNetworkName = "networkName";
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .developerLoggerEnabled(true)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(teeWorkflowConfig.getPostComputeImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPostComputeHeapSize()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPostComputeEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.getPostComputeDockerEnv(SECURE_SESSION_ID, TEE_POST_COMPUTE_HEAP))
                .thenReturn(env);
        String iexecOutBind = iexecOut + ":" + IexecFileHelper.SLASH_IEXEC_OUT;
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn(iexecOutBind);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        when(workerConfigService.getDockerNetworkName()).thenReturn(lasNetworkName);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(true).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION_ID);

        assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(dockerService, times(1)).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .chainTaskId(CHAIN_TASK_ID)
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID +
                                "-tee-post-compute")
                        .imageUri(TEE_POST_COMPUTE_IMAGE)
                        .entrypoint(TEE_POST_COMPUTE_ENTRYPOINT)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(env)
                        .binds(Collections.singletonList(iexecOutBind))
                        .isSgx(true)
                        .dockerNetwork(lasNetworkName)
                        .shouldDisplayLogs(true)
                        .build()
        );
    }

    @Test
    public void shouldNotRunTeePostComputeSinceDockerImageNotFoundLocally() {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .developerLoggerEnabled(true)
                .build();
        when(teeWorkflowConfig.getPostComputeImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPostComputeHeapSize()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPostComputeEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(false);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION_ID);
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        verify(dockerService, never()).run(any());
    }

    @Test
    public void shouldRunTeePostComputeWithFailDockerResponse() {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .developerLoggerEnabled(true)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(teeWorkflowConfig.getPostComputeImage()).thenReturn(TEE_POST_COMPUTE_IMAGE);
        when(teeWorkflowConfig.getPostComputeHeapSize()).thenReturn(TEE_POST_COMPUTE_HEAP);
        when(teeWorkflowConfig.getPostComputeEntrypoint()).thenReturn(TEE_POST_COMPUTE_ENTRYPOINT);
        when(dockerClientInstanceMock.isImagePresent(TEE_POST_COMPUTE_IMAGE))
                .thenReturn(true);
        when(teeSconeService.getPostComputeDockerEnv(SECURE_SESSION_ID, TEE_POST_COMPUTE_HEAP))
                .thenReturn(env);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        when(workerConfigService.getDockerNetworkName()).thenReturn("lasNetworkName");
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(false).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        PostComputeResponse postComputeResponse =
                postComputeService.runTeePostCompute(taskDescription, SECURE_SESSION_ID);

        assertThat(postComputeResponse.isSuccessful()).isFalse();
        verify(dockerService, times(1)).run(any());
    }

}