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

package com.iexec.worker.compute.app;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.utils.IexecEnvUtils;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.SgxDriverMode;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AppComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final TeeServicesManager teeServicesManager;
    private final SgxService sgxService;
    private final ComputeDurationsService appComputeDurationsService;

    public AppComputeService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            TeeServicesManager teeServicesManager,
            SgxService sgxService,
            ComputeDurationsService appComputeDurationsService) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.teeServicesManager = teeServicesManager;
        this.sgxService = sgxService;
        this.appComputeDurationsService = appComputeDurationsService;
    }

    public AppComputeResponse runCompute(TaskDescription taskDescription,
                                         TeeSessionGenerationResponse secureSession) {
        String chainTaskId = taskDescription.getChainTaskId();
        final List<String> env = IexecEnvUtils.getComputeStageEnvList(taskDescription);

        final List<Bind> binds = new ArrayList<>();
        binds.add(Bind.parse(dockerService.getInputBind(chainTaskId)));
        binds.add(Bind.parse(dockerService.getIexecOutBind(chainTaskId)));

        if (taskDescription.isTeeTask()) {
            final TeeService teeService = teeServicesManager
                    .getTeeService(taskDescription.getTeeFramework());

            final List<String> strings = teeService
                    .buildComputeDockerEnv(taskDescription, secureSession);
            env.addAll(strings);

            final List<Bind> additionalBindings =
                    teeService.getAdditionalBindings().stream().map(Bind::parse).toList();
            binds.addAll(additionalBindings);
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(binds)
                .withDevices(sgxService.getSgxDevices());
        // Enclave should be able to connect to the LAS
        if (taskDescription.isTeeTask()) {
            hostConfig.withNetworkMode(workerConfigService.getDockerNetworkName());
        }
        DockerRunRequest runRequest = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .chainTaskId(chainTaskId)
                .imageUri(taskDescription.getAppUri())
                .containerName(getTaskContainerName(chainTaskId))
                .cmd(taskDescription.getDealParams().getIexecArgs())
                .env(env)
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .sgxDriverMode(
                        taskDescription.isTeeTask()
                                ? sgxService.getSgxDriverMode()
                                : SgxDriverMode.NONE
                )
                .build();
        DockerRunResponse dockerResponse = dockerService.run(runRequest);
        final Duration executionDuration = dockerResponse.getExecutionDuration();
        if (executionDuration != null) {
            appComputeDurationsService.addDurationForTask(chainTaskId, executionDuration.toMillis());
        }
        final DockerRunFinalStatus finalStatus = dockerResponse.getFinalStatus();
        return AppComputeResponse.builder()
                .exitCauses(getExitCauseFromFinalStatus(finalStatus))
                .stdout(dockerResponse.getStdout())
                .stderr(dockerResponse.getStderr())
                .exitCode(dockerResponse.getContainerExitCode())
                .build();
    }


    // We use the name "worker1-0xabc123" for app container to avoid
    // conflicts when running multiple workers on the same machine.
    // Exp: integration tests
    private String getTaskContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId;
    }

    private List<ReplicateStatusCause> getExitCauseFromFinalStatus(DockerRunFinalStatus finalStatus) {
        if (finalStatus == DockerRunFinalStatus.TIMEOUT) {
            return List.of(ReplicateStatusCause.APP_COMPUTE_TIMEOUT);
        } else if (finalStatus == DockerRunFinalStatus.FAILED) {
            return List.of(ReplicateStatusCause.APP_COMPUTE_FAILED);
        }
        return List.of();
    }
}
