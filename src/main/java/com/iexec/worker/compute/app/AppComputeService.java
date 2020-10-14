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
import com.iexec.worker.docker.DockerRunRequest;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.tee.scone.SconeTeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


@Slf4j
@Service
public class AppComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final PublicConfigurationService publicConfigService;
    private final SconeTeeService sconeTeeService;

    public AppComputeService(
            WorkerConfigurationService workerConfigService,
            PublicConfigurationService publicConfigService,
            DockerService dockerService,
            SconeTeeService sconeTeeService
    ) {
        this.workerConfigService = workerConfigService;
        this.publicConfigService = publicConfigService;
        this.dockerService = dockerService;
        this.sconeTeeService = sconeTeeService;
    }

    public ComputeResponse runCompute(TaskDescription taskDescription,
                                      String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> env = EnvUtils.getContainerEnvList(taskDescription);
        if (taskDescription.isTeeTask()) {
            List<String> strings = sconeTeeService.buildSconeDockerEnv(
                    secureSessionId + "/app",
                    publicConfigService.getSconeCasURL(),
                    "1G");
            env.addAll(strings);
        }

        List<String> binds = Arrays.asList(
                workerConfigService.getTaskInputDir(chainTaskId) + ":" + FileHelper.SLASH_IEXEC_IN,
                workerConfigService.getTaskIexecOutDir(chainTaskId) + ":" + FileHelper.SLASH_IEXEC_OUT
        );

        return dockerService.run(
                DockerRunRequest.builder()
                        .imageUri(taskDescription.getAppUri())
                        .containerName(getTaskContainerName(chainTaskId))
                        .cmd(taskDescription.getCmd())
                        .env(env)
                        .binds(binds)
                        .maxExecutionTime(taskDescription.getMaxExecutionTime())
                        .isSgx(taskDescription.isTeeTask())
                        .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                        .build());
    }


    // We use the name "worker1-0xabc123" for app container to avoid
    // conflicts when running multiple workers on the same machine.
    // Exp: integration tests
    private String getTaskContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId;
    }
}