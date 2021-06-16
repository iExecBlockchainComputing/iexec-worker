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
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.worker.compute.TeeWorkflowConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.TeeSconeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class PostComputeService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final ResultService resultService;
    private final TeeSconeService teeSconeService;
    private final TeeWorkflowConfiguration teeWorkflowConfig;

    public PostComputeService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            ResultService resultService,
            TeeSconeService teeSconeService,
            TeeWorkflowConfiguration teeWorkflowConfig) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.resultService = resultService;
        this.teeSconeService = teeSconeService;
        this.teeWorkflowConfig = teeWorkflowConfig;
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

    public PostComputeResponse runTeePostCompute(TaskDescription taskDescription, String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        String postComputeImage = teeWorkflowConfig.getPostComputeImage();
        long postComputeHeapSize = teeWorkflowConfig.getPostComputeHeapSize();
        // ###############################################################################
        // TODO: activate this when user specific post-compute is properly
        // supported. See https://github.com/iExecBlockchainComputing/iexec-sms/issues/52.
        // ###############################################################################
        // // Use specific post-compute image if requested.
        // if (taskDescription.containsPostCompute()) {
        //     postComputeImage = taskDescription.getTeePostComputeImage();
        //     postComputeHeapSize = taskDescription.getTeePostComputeHeapSize();
        //     pull image
        // }
        // ###############################################################################
        if (!dockerService.getClient().isImagePresent(postComputeImage)) {
            log.error("Tee post-compute image not found locally [chainTaskId:{}]",
                    chainTaskId);
            return PostComputeResponse.builder().isSuccessful(false).build();
        }
        List<String> env = teeSconeService.
                getPostComputeDockerEnv(secureSessionId, postComputeHeapSize);
        List<String> binds =
                Collections.singletonList(dockerService.getIexecOutBind(chainTaskId));

        DockerRunResponse dockerResponse = dockerService.run(
                DockerRunRequest.builder()
                        .chainTaskId(chainTaskId)
                        .containerName(getTaskTeePostComputeContainerName(chainTaskId))
                        .imageUri(postComputeImage)
                        .entrypoint(teeWorkflowConfig.getPostComputeEntrypoint())
                        .maxExecutionTime(taskDescription.getMaxExecutionTime())
                        .env(env)
                        .binds(binds)
                        .isSgx(true)
                        .dockerNetwork(workerConfigService.getDockerNetworkName())
                        .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                        .build());
        return PostComputeResponse.builder()
                .isSuccessful(dockerResponse.isSuccessful())
                .stdout(dockerResponse.getStdout())
                .stderr(dockerResponse.getStderr())
                .build();
    }

    private String getTaskTeePostComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-post-compute";
    }

}