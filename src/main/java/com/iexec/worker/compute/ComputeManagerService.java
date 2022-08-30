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
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.app.AppComputeService;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.post.PostComputeService;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.compute.pre.PreComputeService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerRegistryConfiguration;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service
public class ComputeManagerService {

    private static final String STDOUT_FILENAME = "stdout.txt";
    private static final String STDERR_FILENAME = "stderr.txt";

    private final Map<Long, Long> categoryTimeoutMap = new HashMap<>(5);

    private final DockerService dockerService;
    private final DockerRegistryConfiguration dockerRegistryConfiguration;
    private final PreComputeService preComputeService;
    private final AppComputeService appComputeService;
    private final PostComputeService postComputeService;
    private final WorkerConfigurationService workerConfigService;
    private final ResultService resultService;

    public ComputeManagerService(
            DockerService dockerService,
            DockerRegistryConfiguration dockerRegistryConfiguration,
            PreComputeService preComputeService,
            AppComputeService appComputeService,
            PostComputeService postComputeService,
            WorkerConfigurationService workerConfigService,
            ResultService resultService) {
        this.dockerService = dockerService;
        this.dockerRegistryConfiguration = dockerRegistryConfiguration;
        this.preComputeService = preComputeService;
        this.appComputeService = appComputeService;
        this.postComputeService = postComputeService;
        this.workerConfigService = workerConfigService;
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

        final long pullTimeout = computeImagePullTimeout(taskDescription);
        return dockerService.getClient(taskDescription.getAppUri())
                .pullImage(taskDescription.getAppUri(), Duration.of(pullTimeout, ChronoUnit.MINUTES));
    }

    /**
     * Computes image pull timeout depending on task max time execution.
     * This should depend on task category (XS, S, M, L, XL).
     * <p>
     * Current formula is: {@literal 10 * log(maxExecutionTime / 10)},
     * with {@literal maxExecutionTime} being expressed in minutes.
     * The result is rounded to the nearest integer.
     * <p>
     * Examples of timeout depending on category
     * - with default values of 5 for min and 30 for max:
     * <ul>
     *     <li>XS : 10 * log(    50 / 10) =  7 minutes</li>
     *     <li>S  : 10 * log(   200 / 10) = 13 minutes</li>
     *     <li>M  : 10 * log(   600 / 10) = 18 minutes</li>
     *     <li>L  : 10 * log(  1800 / 10) = 23 minutes</li>
     *     <li>XL : 10 * log(  6000 / 10) = 28 minutes</li>
     * </ul>
     * If computed timeout duration is lower than {@link DockerRegistryConfiguration#getMinPullTimeout()},
     * then final timeout duration is {@link DockerRegistryConfiguration#getMinPullTimeout()}.
     * <br>
     * If computed timeout duration is greater than {@link DockerRegistryConfiguration#getMaxPullTimeout()},
     * then final timeout duration is {@link DockerRegistryConfiguration#getMaxPullTimeout()}.
     * <br>
     * If {@link DockerRegistryConfiguration#getMinPullTimeout()} is greater than {@link DockerRegistryConfiguration#getMaxPullTimeout()},
     * then {@link DockerRegistryConfiguration#getMaxPullTimeout()} is the one used.
     */
    long computeImagePullTimeout(TaskDescription taskDescription) {
        final long maxExecutionTimeInMinutes = taskDescription.getMaxExecutionTime() / 60;
        if (categoryTimeoutMap.containsKey(maxExecutionTimeInMinutes)) {
            return categoryTimeoutMap.get(maxExecutionTimeInMinutes);
        }
        final long imagePullTimeout = Math.min(
                Math.max(
                        Math.round(10.0 * Math.log10(maxExecutionTimeInMinutes / 10.0)),
                        dockerRegistryConfiguration.getMinPullTimeout().toMinutes()
                ),
                dockerRegistryConfiguration.getMaxPullTimeout().toMinutes()
        );
        categoryTimeoutMap.put(maxExecutionTimeInMinutes, imagePullTimeout);
        return imagePullTimeout;
    }

    public boolean isAppDownloaded(String imageUri) {
        return dockerService.getClient().isImagePresent(imageUri);
    }

    /**
     * Standard tasks: download secrets && decrypt dataset (TODO: rewritte or remove)
     * <p>
     * TEE tasks: download pre-compute and post-compute images,
     * create SCONE secure session, and run pre-compute container.
     *
     * @param taskDescription
     * @param workerpoolAuth
     * @return
     */
    public PreComputeResponse runPreCompute(TaskDescription taskDescription,
                                            WorkerpoolAuthorization workerpoolAuth) {
        log.info("Running pre-compute [chainTaskId:{}, isTee:{}]",
                taskDescription.getChainTaskId(),
                taskDescription.isTeeTask());

        if (taskDescription.isTeeTask()) {
            return preComputeService.runTeePreCompute(taskDescription,
                            workerpoolAuth);
        }
        return PreComputeResponse.builder().build();
    }

    public AppComputeResponse runCompute(TaskDescription taskDescription,
                                         String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Running compute [chainTaskId:{}, isTee:{}]", chainTaskId,
                taskDescription.isTeeTask());

        AppComputeResponse appComputeResponse =
                appComputeService.runCompute(taskDescription, secureSessionId);

        if (appComputeResponse.isSuccessful()) {
            writeLogs(chainTaskId, STDOUT_FILENAME, appComputeResponse.getStdout());
            writeLogs(chainTaskId, STDERR_FILENAME, appComputeResponse.getStderr());
        }
        return appComputeResponse;
    }

    private void writeLogs(String chainTaskId, String filename, String logs) {
        if (!logs.isEmpty()) {
            String filePath = workerConfigService.getTaskIexecOutDir(chainTaskId) + File.separator + filename;
            File file = FileHelper.createFileWithContent(filePath, logs);
            log.info("Saved logs file [path:{}]",
                    file.getAbsolutePath());
            //TODO Make sure file is properly written
        }
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
        PostComputeResponse postComputeResponse = PostComputeResponse.builder()
                .exitCause(ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE)
                .build();

        if (!taskDescription.isTeeTask()) {
            postComputeResponse = postComputeService.runStandardPostCompute(taskDescription);
        } else if (!secureSessionId.isEmpty()) {
            postComputeResponse = postComputeService.runTeePostCompute(taskDescription, secureSessionId);
        }
        if (!postComputeResponse.isSuccessful()) {
            return postComputeResponse;
        }
        ComputedFile computedFile = resultService.readComputedFile(chainTaskId);
        if (computedFile == null) {
            postComputeResponse.setExitCause(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND);
            return postComputeResponse;
        }
        String resultDigest = resultService.computeResultDigest(computedFile);
        if (resultDigest.isEmpty()) {
            postComputeResponse.setExitCause(ReplicateStatusCause.POST_COMPUTE_RESULT_DIGEST_COMPUTATION_FAILED);
        }
        resultService.saveResultInfo(chainTaskId, taskDescription, computedFile);
        return postComputeResponse;
    }

}
