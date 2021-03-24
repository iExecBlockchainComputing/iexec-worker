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

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.IexecEnvUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.tee.scone.SconeLasConfiguration;
import com.iexec.worker.tee.scone.SconeTeeService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class AppComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final SconeTeeService sconeTeeService;
    private final SconeLasConfiguration sconeLasConfiguration;

    public AppComputeService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            SconeTeeService sconeTeeService,
            SconeLasConfiguration sconeLasConfiguration
    ) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.sconeTeeService = sconeTeeService;
        this.sconeLasConfiguration = sconeLasConfiguration;
    }

    public AppComputeResponse runCompute(TaskDescription taskDescription,
                                      String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> env = IexecEnvUtils.getComputeStageEnvList(taskDescription);
        if (taskDescription.isTeeTask()) {
            List<String> strings = sconeTeeService.getComputeDockerEnv(secureSessionId);
            env.addAll(strings);
        }

        List<String> binds = Arrays.asList(
                dockerService.getIexecInBind(chainTaskId),
                dockerService.getIexecOutBind(chainTaskId)
        );

        DockerRunRequest runRequest = DockerRunRequest.builder()
                .chainTaskId(chainTaskId)
                .imageUri(taskDescription.getAppUri())
                .containerName(getTaskContainerName(chainTaskId))
                .cmd(taskDescription.getCmd())
                .env(env)
                .binds(binds)
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .isSgx(taskDescription.isTeeTask())
                .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                .build();
        // Enclave should be able to connect to the LAS
        if (taskDescription.isTeeTask()) {
            runRequest.setDockerNetwork(sconeLasConfiguration.getDockerNetworkName());
        }
        DockerRunResponse dockerResponse = dockerService.run(runRequest);
        return AppComputeResponse.builder()
                .isSuccessful(dockerResponse.isSuccessful())
                .stdout(dockerResponse.getStdout())
                .stderr(dockerResponse.getStderr())
                .build();
    }


    // We use the name "worker1-0xabc123" for app container to avoid
    // conflicts when running multiple workers on the same machine.
    // Exp: integration tests
    private String getTaskContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId;
    }
}