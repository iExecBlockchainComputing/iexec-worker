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

package com.iexec.worker.compute.app;

import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.EnvUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.compute.ComputeResponse;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.tee.scone.SconeTeeService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

public class AppComputeServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String APP_URI = "APP_URI";
    private final static String SCONE_CAS_URL = "SCONE_CAS_URL";
    private final static String WORKER_NAME = "WORKER_NAME";
    private final static String TEE_POST_COMPUTE_IMAGE =
            "TEE_POST_COMPUTE_IMAGE";
    private final static String SECURE_SESSION_ID = "SECURE_SESSION_ID";
    private final static long MAX_EXECUTION_TIME = 1000;
    private final static String INPUT = "INPUT";
    private final static String IEXEC_OUT = "IEXEC_OUT";

    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .appUri(APP_URI)
            .datasetUri(DATASET_URI)
            .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
            .maxExecutionTime(MAX_EXECUTION_TIME)
            .developerLoggerEnabled(true)
            .inputFiles(Arrays.asList("file0", "file1"))
            .isTeeTask(true)
            .build();

    @InjectMocks
    private AppComputeService appComputeService;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerService dockerService;
    @Mock
    private PublicConfigurationService publicConfigService;
    @Mock
    private SconeTeeService sconeTeeService;

    @Before
    public void beforeEach() throws IOException {
        MockitoAnnotations.initMocks(this);
        when(publicConfigService.getSconeCasURL()).thenReturn(SCONE_CAS_URL);

    }

    @Test
    public void shouldRunCompute() {
        taskDescription.setTeeTask(false);
        when(workerConfigService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn(INPUT);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(IEXEC_OUT);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(true).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        ComputeResponse computeResponse =
                appComputeService.runCompute(taskDescription,
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
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID)
                        .imageUri(APP_URI)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(EnvUtils.getContainerEnvList(taskDescription))
                        .binds(
                                Arrays.asList(INPUT + ":" + FileHelper.SLASH_IEXEC_IN,
                                        IEXEC_OUT + ":" + FileHelper.SLASH_IEXEC_OUT)
                        )
                        .isSgx(false)
                        .shouldDisplayLogs(true)
                        .build()
        );
    }

    @Test
    public void shouldRunComputeWithTee() {
        taskDescription.setTeeTask(true);
        when(sconeTeeService.buildSconeDockerEnv(
                SECURE_SESSION_ID + "/app",
                SCONE_CAS_URL,
                "1G")).thenReturn(Arrays.asList("var0", "var1"));
        List<String> env = new ArrayList<>(Arrays.asList("var0", "var1"));
        env.addAll(EnvUtils.getContainerEnvList(taskDescription));
        Collections.sort(env);
        when(workerConfigService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn(INPUT);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(IEXEC_OUT);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(true).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        ComputeResponse computeResponse =
                appComputeService.runCompute(taskDescription,
                        SECURE_SESSION_ID);

        Assertions.assertThat(computeResponse).isEqualTo(expectedDockerRunResponse);
        verify(dockerService, times(1)).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        Collections.sort(dockerRunRequest.getEnv());
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID)
                        .imageUri(APP_URI)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(env)
                        .binds(
                                Arrays.asList(INPUT + ":" + FileHelper.SLASH_IEXEC_IN,
                                        IEXEC_OUT + ":" + FileHelper.SLASH_IEXEC_OUT)
                        )
                        .isSgx(true)
                        .shouldDisplayLogs(true)
                        .build()
        );
    }

    @Test
    public void shouldRunComputeWithFailDockerResponse() {
        taskDescription.setTeeTask(false);
        when(workerConfigService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn(INPUT);
        when(workerConfigService.getTaskIexecOutDir(CHAIN_TASK_ID)).thenReturn(IEXEC_OUT);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().isSuccessful(false).build();
        when(dockerService.getClient().run(any())).thenReturn(expectedDockerRunResponse);

        ComputeResponse computeResponse =
                appComputeService.runCompute(taskDescription,
                        SECURE_SESSION_ID);

        Assertions.assertThat(computeResponse).isEqualTo(expectedDockerRunResponse);
        verify(dockerService, times(1)).run(any());
    }

}