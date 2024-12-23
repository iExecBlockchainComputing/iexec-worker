/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.common.utils.IexecEnvUtils;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppComputeServiceTests {

    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private static final String DATASET_URI = "DATASET_URI";
    private static final String APP_URI = "APP_URI";
    private static final String WORKER_NAME = "WORKER_NAME";
    private static final TeeSessionGenerationResponse SECURE_SESSION = mock(TeeSessionGenerationResponse.class);
    private static final long MAX_EXECUTION_TIME = 1000;
    private static final String INPUT = "INPUT";
    private static final String IEXEC_OUT = "IEXEC_OUT";
    public static final long HEAP_SIZE = 1024;

    private final DealParams dealParams = DealParams.builder()
            .iexecInputFiles(Arrays.asList("file0", "file1"))
            .build();

    private final TaskDescription.TaskDescriptionBuilder taskDescriptionBuilder = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .appUri(APP_URI)
            .datasetUri(DATASET_URI)
            .maxExecutionTime(MAX_EXECUTION_TIME)
            .dealParams(dealParams)
            .isTeeTask(true);

    @InjectMocks
    private AppComputeService appComputeService;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerService dockerService;
    @Mock
    private TeeServicesManager teeServicesManager;
    @Mock
    private SgxService sgxService;
    @Mock
    private ComputeDurationsService appComputeDurationsService;

    @Mock
    private TeeService teeMockedService;

    @Test
    void shouldRunCompute() {
        final TaskDescription taskDescription = taskDescriptionBuilder
                .isTeeTask(false)
                .build();
        String inputBind = INPUT + ":" + IexecFileHelper.SLASH_IEXEC_IN;
        when(dockerService.getInputBind(CHAIN_TASK_ID)).thenReturn(inputBind);
        String iexecOutBind = IEXEC_OUT + ":" + IexecFileHelper.SLASH_IEXEC_OUT;
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn(iexecOutBind);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse = DockerRunResponse
                .builder()
                .finalStatus(DockerRunFinalStatus.SUCCESS)
                .executionDuration(Duration.ofSeconds(10))
                .build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                appComputeService.runCompute(taskDescription,
                        SECURE_SESSION);

        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        verify(dockerService).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(Bind.parse(inputBind), Bind.parse(iexecOutBind))
                .withDevices(new ArrayList<>());
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .hostConfig(hostConfig)
                        .chainTaskId(CHAIN_TASK_ID)
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID)
                        .imageUri(APP_URI)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(IexecEnvUtils.getComputeStageEnvList(taskDescription))
                        .sgxDriverMode(SgxDriverMode.NONE)
                        .build()
        );
    }

    @Test
    void shouldRunComputeWithTeeAndConnectAppToLas() {
        final TaskDescription taskDescription = taskDescriptionBuilder
                .appEnclaveConfiguration(
                        TeeEnclaveConfiguration.builder().heapSize(HEAP_SIZE).build())
                .build();
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeMockedService.buildComputeDockerEnv(taskDescription, SECURE_SESSION))
                .thenReturn(Arrays.asList("var0", "var1"));
        List<String> env = new ArrayList<>(Arrays.asList("var0", "var1"));
        env.addAll(IexecEnvUtils.getComputeStageEnvList(taskDescription));
        Collections.sort(env);
        String inputBind = INPUT + ":" + IexecFileHelper.SLASH_IEXEC_IN;
        when(dockerService.getInputBind(CHAIN_TASK_ID)).thenReturn(inputBind);
        String iexecOutBind = IEXEC_OUT + ":" + IexecFileHelper.SLASH_IEXEC_OUT;
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn(iexecOutBind);
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        String lasNetworkName = "lasNetworkName";
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

        AppComputeResponse appComputeResponse =
                appComputeService.runCompute(taskDescription, SECURE_SESSION);

        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        verify(dockerService).run(any());
        ArgumentCaptor<DockerRunRequest> argumentCaptor =
                ArgumentCaptor.forClass(DockerRunRequest.class);
        verify(dockerService).run(argumentCaptor.capture());
        DockerRunRequest dockerRunRequest =
                argumentCaptor.getAllValues().get(0);
        Collections.sort(dockerRunRequest.getEnv());
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(Bind.parse(inputBind), Bind.parse(iexecOutBind))
                .withDevices(devices)
                .withNetworkMode(lasNetworkName);
        Assertions.assertThat(dockerRunRequest).isEqualTo(
                DockerRunRequest.builder()
                        .hostConfig(hostConfig)
                        .chainTaskId(CHAIN_TASK_ID)
                        .containerName(WORKER_NAME + "-" + CHAIN_TASK_ID)
                        .imageUri(APP_URI)
                        .maxExecutionTime(MAX_EXECUTION_TIME)
                        .env(env)
                        .sgxDriverMode(SgxDriverMode.LEGACY)
                        .build()
        );
    }

    @Test
    void shouldRunComputeWithFailDockerResponse() {
        final TaskDescription taskDescription = taskDescriptionBuilder
                .isTeeTask(false)
                .build();
        when(dockerService.getInputBind(CHAIN_TASK_ID)).thenReturn("/iexec_in:/iexec_in");
        when(dockerService.getIexecOutBind(CHAIN_TASK_ID)).thenReturn("/iexec_out:/iexec_out");
        when(workerConfigService.getWorkerName()).thenReturn(WORKER_NAME);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder().finalStatus(DockerRunFinalStatus.FAILED).build();
        when(dockerService.run(any())).thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                appComputeService.runCompute(taskDescription,
                        SECURE_SESSION);

        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        verify(dockerService).run(any());
    }

}
