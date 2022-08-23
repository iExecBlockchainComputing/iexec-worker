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

import com.iexec.common.docker.DockerRunFinalStatus;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.sgx.SgxDriverMode;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.IexecEnvUtils;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AppComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final TeeServicesManager teeServicesManager;
    private final SgxService sgxService;

    public AppComputeService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            TeeServicesManager teeServicesManager,
            SgxService sgxService) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.teeServicesManager = teeServicesManager;
        this.sgxService = sgxService;
    }

    public AppComputeResponse runCompute(TaskDescription taskDescription,
                                        TeeSessionGenerationResponse secureSession) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> env = IexecEnvUtils.getComputeStageEnvList(taskDescription);

        List<String> binds = new ArrayList<>(Arrays.asList(
                dockerService.getInputBind(chainTaskId),
                dockerService.getIexecOutBind(chainTaskId)
        ));

        if (taskDescription.isTeeTask()) {
            final TeeService teeService = teeServicesManager
                    .getTeeService(taskDescription.getTeeEnclaveProvider());

            final List<String> strings = teeService
                    .buildComputeDockerEnv(taskDescription, secureSession);
            env.addAll(strings);

            binds.addAll(teeService.getBindings());
        }

        DockerRunRequest runRequest = DockerRunRequest.builder()
                .chainTaskId(chainTaskId)
                .imageUri(taskDescription.getAppUri())
                .containerName(getTaskContainerName(chainTaskId))
                .cmd(taskDescription.getCmd())
                .env(env)
                .binds(binds)
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .sgxDriverMode(
                        taskDescription.isTeeTask()
                                ? sgxService.getSgxDriverMode()
                                : SgxDriverMode.NONE
                )
                .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                .build();
        // Enclave should be able to connect to the LAS
        if (taskDescription.isTeeTask()) {
            runRequest.setDockerNetwork(workerConfigService.getDockerNetworkName());
        }
        DockerRunResponse dockerResponse = dockerService.run(runRequest);
        final DockerRunFinalStatus finalStatus = dockerResponse.getFinalStatus();
        return AppComputeResponse.builder()
                .exitCause(getExitCauseFromFinalStatus(finalStatus))
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

    private ReplicateStatusCause getExitCauseFromFinalStatus(DockerRunFinalStatus finalStatus) {
        if (finalStatus == DockerRunFinalStatus.TIMEOUT) {
            return ReplicateStatusCause.APP_COMPUTE_TIMEOUT;
        } else if (finalStatus == DockerRunFinalStatus.FAILED) {
            return ReplicateStatusCause.APP_COMPUTE_FAILED;
        }
        return null;
    }
}