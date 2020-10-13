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

import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.worker.compute.ComputeResponse;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerRunRequest;
import com.iexec.worker.docker.DockerRunResponse;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class PostComputeServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String SCONE_CAS_URL = "SCONE_CAS_URL";
    private final static String WORKER_NAME = "WORKER_NAME";
    private final static String TEE_POST_COMPUTE_IMAGE =
            "TEE_POST_COMPUTE_IMAGE";
    private final static String SECURE_SESSION_ID = "SECURE_SESSION_ID";
    private final static long MAX_EXECUTION_TIME = 1000;

    @Rule
    public TemporaryFolder jUnitTemporaryFolder = new TemporaryFolder();
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
    private SconeTeeService sconeTeeService;

    @Before
    public void beforeEach() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(publicConfigService.getSconeCasURL()).thenReturn(SCONE_CAS_URL);
        output = jUnitTemporaryFolder.newFolder().getAbsolutePath();
        iexecOut = output + FileHelper.SLASH_IEXEC_OUT;
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
    public void shouldRunTeePostCompute() {
        taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(DATASET_URI)
                .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .developerLoggerEnabled(true)
                .build();
        List<String> env = Arrays.asList("var0", "var1");
        when(sconeTeeService.buildSconeDockerEnv(SECURE_SESSION_ID + "/post" +
                "-compute", SCONE_CAS_URL, "3G")).thenReturn(env);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(true).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        ComputeResponse computeResponse =
                postComputeService.runTeePostCompute(taskDescription,
                        SECURE_SESSION_ID);

        Assertions.assertThat(computeResponse).isEqualTo(expectedDockerRunResponse);
        verify(dockerService, times(1)).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID +
                                "-tee-post-compute")
                        .imageUri(TEE_POST_COMPUTE_IMAGE)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(env)
                        .binds(Arrays.asList(iexecOut + ":" + FileHelper.SLASH_IEXEC_OUT,
                                output + ":" + FileHelper.SLASH_OUTPUT))
                        .isSgx(true)
                        .shouldDisplayLogs(true)
                        .build()
        );
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
        when(sconeTeeService.buildSconeDockerEnv(SECURE_SESSION_ID + "/post" +
                "-compute", SCONE_CAS_URL, "3G")).thenReturn(env);
        when(workerConfigService.getTaskOutputDir(CHAIN_TASK_ID)).thenReturn(output);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(iexecOut);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(false).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        ComputeResponse computeResponse =
                postComputeService.runTeePostCompute(taskDescription,
                        SECURE_SESSION_ID);

        Assertions.assertThat(computeResponse).isEqualTo(expectedDockerRunResponse);
        verify(dockerService, times(1)).run(any());
    }

}