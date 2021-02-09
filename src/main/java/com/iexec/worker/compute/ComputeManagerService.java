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
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.app.AppComputeService;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.post.PostComputeService;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.compute.pre.PreComputeService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;


@Slf4j
@Service
public class ComputeManagerService {

    private static final String STDOUT_FILENAME = "stdout.txt";

    private final DockerService dockerService;
    private final PreComputeService preComputeService;
    private final AppComputeService appComputeService;
    private final PostComputeService postComputeService;
    private final WorkerConfigurationService workerConfigService;
    private final IexecHubService iexecHubService;
    private final ResultService resultService;

    public ComputeManagerService(
            DockerService dockerService,
            PreComputeService preComputeService,
            AppComputeService appComputeService,
            PostComputeService postComputeService,
            WorkerConfigurationService workerConfigService,
            IexecHubService iexecHubService,
            ResultService resultService
    ) {
        this.dockerService = dockerService;
        this.preComputeService = preComputeService;
        this.appComputeService = appComputeService;
        this.postComputeService = postComputeService;
        this.workerConfigService = workerConfigService;
        this.iexecHubService = iexecHubService;
        this.resultService = resultService;
    }

    public boolean downloadApp(TaskDescription taskDescription) {
        if (taskDescription == null || taskDescription.getAppType() == null) {
            return false;
        }
        boolean isDockerType =
                taskDescription.getAppType().equals(DappType.DOCKER);
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
    public PreComputeResponse runPreCompute(TaskDescription taskDescription,
                                            WorkerpoolAuthorization workerpoolAuth) {
        log.info("Running pre-compute [chainTaskId:{}, isTee:{}]",
                taskDescription.getChainTaskId(),
                taskDescription.isTeeTask());

        if (taskDescription.isTeeTask()) {
            String secureSessionId =
                    preComputeService.runTeePreCompute(taskDescription,
                            workerpoolAuth);
            return PreComputeResponse.builder()
                    .isTeeTask(true)
                    .secureSessionId(secureSessionId)
                    .build();
        }

        return PreComputeResponse.builder()
                .isSuccessful(
                        preComputeService.runStandardPreCompute(taskDescription))
                .build();
    }

    public AppComputeResponse runCompute(TaskDescription taskDescription,
                                         String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Running compute [chainTaskId:{}, isTee:{}]", chainTaskId,
                taskDescription.isTeeTask());

        ComputeResponse computeResponse =
                appComputeService.runCompute(taskDescription, secureSessionId);

        if (computeResponse.isSuccessful() && !computeResponse.getStdout().isEmpty()) {
            // save /output/stdout.txt file
            String stdoutFilePath =
                    workerConfigService.getTaskIexecOutDir(chainTaskId) + File.separator + STDOUT_FILENAME;
            File stdoutFile = FileHelper.createFileWithContent(stdoutFilePath
                    , computeResponse.getStdout());
            log.info("Saved stdout file [path:{}]",
                    stdoutFile.getAbsolutePath());
            //TODO Make sure stdout is properly written
        }

        return AppComputeResponse.builder()
                .isSuccessful(computeResponse.isSuccessful())
                .stdout(computeResponse.getStdout())
                .stderr(computeResponse.getStderr())
                .build();
    }

    /*
     * - Copy computed.json file produced by the compute stage to /output
     * - Zip iexec_out folder
     * For TEE tasks, worker-tee-post-compute will do those two steps since
     * all files in are protected.
     *
     * - Save stdout file
     */
    public PostComputeResponse runPostCompute(TaskDescription taskDescription,
                                              String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Running post-compute [chainTaskId:{}, isTee:{}]",
                chainTaskId, taskDescription.isTeeTask());
        PostComputeResponse postComputeResponse = new PostComputeResponse();

        boolean isSuccessful;
        if (taskDescription.isTeeTask() && !secureSessionId.isEmpty()) {
            ComputeResponse computeResponse = postComputeService.runTeePostCompute(taskDescription,
                    secureSessionId);
            isSuccessful = computeResponse.isSuccessful();
            postComputeResponse.setStdout(computeResponse.getStdout());
            postComputeResponse.setStderr(computeResponse.getStderr());
        } else {
            isSuccessful = postComputeService.runStandardPostCompute(taskDescription);
        }
        if (isSuccessful){
            ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
            if (computedFile != null){
                postComputeResponse.setSuccessful(true);
                resultService.saveResultInfo(chainTaskId, taskDescription,
                        computedFile);
            }
        }
        return postComputeResponse;
    }


}