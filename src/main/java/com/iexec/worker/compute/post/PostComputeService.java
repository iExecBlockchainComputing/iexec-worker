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
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.worker.compute.ComputeResponse;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerRunRequest;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


@Slf4j
@Service
public class PostComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final PublicConfigurationService publicConfigService;
    private final DockerService dockerService;
    private final ResultService resultService;
    private final SconeTeeService sconeTeeService;

    public PostComputeService(
            WorkerConfigurationService workerConfigService,
            PublicConfigurationService publicConfigService,
            DockerService dockerService,
            ResultService resultService,
            SconeTeeService sconeTeeService
    ) {
        this.workerConfigService = workerConfigService;
        this.publicConfigService = publicConfigService;
        this.dockerService = dockerService;
        this.resultService = resultService;
        this.sconeTeeService = sconeTeeService;
    }

    public boolean runStandardPostCompute(TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();
        // create /output/iexec_out.zip
        ResultUtils.zipIexecOut(workerConfigService.getTaskIexecOutDir(chainTaskId)
                , workerConfigService.getTaskOutputDir(chainTaskId));
        // copy /output/iexec_out/computed.json to /output/computed.json to have the same workflow as TEE.
        boolean isCopied = FileHelper.copyFile(
                workerConfigService.getTaskIexecOutDir(chainTaskId) + IexecFileHelper.SLASH_COMPUTED_JSON,
                workerConfigService.getTaskOutputDir(chainTaskId) + IexecFileHelper.SLASH_COMPUTED_JSON);
        if (!isCopied) {
            log.error("Failed to copy computed.json file to /output [chainTaskId:{}]", chainTaskId);
            return false;
        }
        // encrypt result if needed
        if (taskDescription.isResultEncryption() && !resultService.encryptResult(chainTaskId)) {
            log.error("Failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    public ComputeResponse runTeePostCompute(TaskDescription taskDescription, String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        List<String> env = sconeTeeService.buildSconeDockerEnv(secureSessionId + "/post-compute",
                publicConfigService.getSconeCasURL(), "3G");
        List<String> binds = Arrays.asList(
                workerConfigService.getTaskIexecOutDir(chainTaskId) + ":" + FileHelper.SLASH_IEXEC_OUT,
                workerConfigService.getTaskOutputDir(chainTaskId) + ":" + FileHelper.SLASH_OUTPUT);

        return dockerService.run(
                DockerRunRequest.builder()
                        .containerName(getTaskTeePostComputeContainerName(chainTaskId))
                        .imageUri(taskDescription.getTeePostComputeImage())
                        .maxExecutionTime(taskDescription.getMaxExecutionTime())
                        .env(env)
                        .binds(binds)
                        .isSgx(true)
                        .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                        .build());
    }

    private String getTaskTeePostComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-post-compute";
    }

}