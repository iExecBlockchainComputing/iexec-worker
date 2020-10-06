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

package com.iexec.worker.compute;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerContainerLogs;
import com.iexec.worker.docker.DockerRunResponse;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;


@Slf4j
@Service
public class ComputeManagerService {

    private static final String STDOUT_FILENAME = "stdout.txt";

    private final DockerService dockerService;
    private final PreComputeStepService preComputeStepService;
    private final ComputeStepService computeStepService;
    private final PostComputeStepService postComputeStepService;
    private final WorkerConfigurationService workerConfigService;
    private final IexecHubService iexecHubService;

    public ComputeManagerService(
            DockerService dockerService,
            PreComputeStepService preComputeStepService,
            ComputeStepService computeStepService,
            PostComputeStepService postComputeStepService,
            WorkerConfigurationService workerConfigService,
            IexecHubService iexecHubService
    ) {
        this.dockerService = dockerService;
        this.preComputeStepService = preComputeStepService;
        this.computeStepService = computeStepService;
        this.postComputeStepService = postComputeStepService;
        this.workerConfigService = workerConfigService;
        this.iexecHubService = iexecHubService;
    }

    public boolean downloadApp(TaskDescription taskDescription) {
        if (taskDescription == null || taskDescription.getAppType() == null) {
            return false;
        }
        boolean isDockerType = taskDescription.getAppType().equals(DappType.DOCKER);
        if (!isDockerType || taskDescription.getAppUri() == null) {
            return false;
        }
        return dockerService.pullImage(taskDescription.getAppUri());
    }

    public boolean isAppDownloaded(String imageUri) {
        return dockerService.isImagePulled(imageUri);
    }

    /*
     * non TEE: download secrets && decrypt dataset (TODO: rewritte or remove)
     *     TEE: download post-compute image && create secure session
     */
    public void runPreCompute(ComputeStage computeStage, TaskDescription taskDescription, WorkerpoolAuthorization workerpoolAuth) {
        log.info("Running pre-compute [chainTaskId:{}, isTee:{}]", taskDescription.getChainTaskId(),
                taskDescription.isTeeTask());
        boolean isSuccessful = false;

        if (taskDescription.isTeeTask()) {
            String secureSessionId = preComputeStepService.runTeePreCompute(taskDescription, workerpoolAuth);
            if (!secureSessionId.isEmpty()) {
                computeStage.setSecureSessionId(secureSessionId);
                isSuccessful = true;
            }
        } else {
            isSuccessful = preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuth);
        }
        computeStage.setPreDockerRunResponse(DockerRunResponse.builder().isSuccessful(isSuccessful).build());
    }

    public void runCompute(ComputeStage computeStage, TaskDescription taskDescription) {
        String chainTaskId = computeStage.getChainTaskId();
        log.info("Running compute [chainTaskId:{}, isTee:{}]", chainTaskId, taskDescription.isTeeTask());

        DockerRunResponse dockerRunResponse = computeStepService.runCompute(computeStage, taskDescription, chainTaskId);

        if (dockerRunResponse.isSuccessful() && dockerRunResponse.getDockerContainerLogs() != null) {
            // save /output/stdout.txt file
            String stdoutFilePath = workerConfigService.getTaskIexecOutDir(chainTaskId) + File.separator + STDOUT_FILENAME;
            File stdoutFile = FileHelper.createFileWithContent(stdoutFilePath, dockerRunResponse.getDockerContainerLogs().getStdout());
            log.info("Saved stdout file [path:{}]", stdoutFile.getAbsolutePath());
        }
        computeStage.setDockerRunResponse(dockerRunResponse);
    }

    /*
     * - Copy computed.json file produced by the compute stage to /output
     * - Zip iexec_out folder
     * For TEE tasks, worker-tee-post-compute will do those two steps since
     * all files in are protected.
     *
     * - Save stdout file
     */
    public void runPostCompute(ComputeStage computeStage, TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Running post-compute [chainTaskId:{}, isTee:{}]", chainTaskId, taskDescription.isTeeTask());
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder().isSuccessful(false).build();

        if (taskDescription.isTeeTask()) {
            dockerRunResponse = postComputeStepService.runTeePostCompute(taskDescription, computeStage.getSecureSessionId());
        } else {
            // TODO Use container
            if (postComputeStepService.runStandardPostCompute(taskDescription)) {
                dockerRunResponse = DockerRunResponse.builder().isSuccessful(true).build();
            }
        }

        if (dockerRunResponse.getDockerContainerLogs() == null) {
            dockerRunResponse.setDockerContainerLogs(DockerContainerLogs.builder().stdout("").build());
        }

        computeStage.setPostDockerRunResponse(dockerRunResponse);
    }


    public ComputedFile getComputedFile(String chainTaskId) {
        ComputedFile computedFile = IexecFileHelper.readComputedFile(chainTaskId,
                workerConfigService.getTaskOutputDir(chainTaskId));
        if (computedFile == null) {
            log.error("Failed to getComputedFile (computed.json missing)[chainTaskId:{}]", chainTaskId);
            return null;
        }
        if (computedFile.getResultDigest() == null || computedFile.getResultDigest().isEmpty()) {
            String resultDigest = computeResultDigest(computedFile);
            if (resultDigest.isEmpty()) {
                log.error("Failed to getComputedFile (resultDigest is empty but cant compute it)" +
                        "[chainTaskId:{}, computedFile:{}]", chainTaskId, computedFile);
                return null;
            }
            computedFile.setResultDigest(resultDigest);
        }
        return computedFile;
    }

    private String computeResultDigest(ComputedFile computedFile) {
        String chainTaskId = computedFile.getTaskId();
        String resultDigest;
        if (iexecHubService.getTaskDescription(chainTaskId).isCallbackRequested()) {
            resultDigest = ResultUtils.computeWeb3ResultDigest(computedFile);
        } else {
            resultDigest = ResultUtils.computeWeb2ResultDigest(computedFile,
                    workerConfigService.getTaskOutputDir(chainTaskId));
        }
        if (resultDigest.isEmpty()) {
            log.error("Failed to computeResultDigest (resultDigest empty)[chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return "";
        }
        return resultDigest;
    }


}