/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_TOO_LONG_RESULT_FILE_NAME;


@Slf4j
@Service
public class PostComputeService {
    private static final int RESULT_FILE_NAME_MAX_LENGTH = 31;

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final TeeServicesManager teeServicesManager;
    private final SgxService sgxService;
    private final ComputeExitCauseService computeExitCauseService;
    private final TeeServicesPropertiesService teeServicesPropertiesService;

    public PostComputeService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            TeeServicesManager teeServicesManager,
            SgxService sgxService,
            ComputeExitCauseService computeExitCauseService,
            TeeServicesPropertiesService teeServicesPropertiesService) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.teeServicesManager = teeServicesManager;
        this.sgxService = sgxService;
        this.computeExitCauseService = computeExitCauseService;
        this.teeServicesPropertiesService = teeServicesPropertiesService;
    }

    /**
     * This method implements the post-compute part of the workflow dedicated to standard tasks.
     * <p>
     * The algorithm is almost the same as the one executed for TEE tasks in a tee-worker-post-compute container:
     * <ul>
     * <li>Creation of the archive containing results as in {@code Web2ResultService#encryptAndUploadResult}
     * <li>Send {@code computed.json} to its final folder as in {@code FlowService#sendComputedFileToHost}
     * </ul>
     * <p>
     * Classes names are inlined in comments for comparison.
     *
     * @param taskDescription description of a standard task
     * @return a post compute response with a cause in case of error. The response is returned to
     * {@link com.iexec.worker.compute.ComputeManagerService}
     * @see com.iexec.worker.compute.ComputeManagerService
     */
    public PostComputeResponse runStandardPostCompute(TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();
        final String taskIexecOutDir = workerConfigService.getTaskIexecOutDir(chainTaskId);
        final String taskOutputDir = workerConfigService.getTaskOutputDir(chainTaskId);

        // check result file names are not too long
        final Optional<ReplicateStatusCause> resultFilesNameError = checkResultFilesName(chainTaskId, taskIexecOutDir);
        if (resultFilesNameError.isPresent()) {
            return PostComputeResponse.builder()
                    .exitCause(resultFilesNameError.get())
                    .build();
        }

        // create /output/iexec_out.zip as in Web2ResultService#encryptAndUploadResult
        // return a POST_COMPUTE_OUT_FOLDER_ZIP_FAILED error on failure
        String zipIexecOutPath = ResultUtils.zipIexecOut(
                taskIexecOutDir,
                taskOutputDir);
        if (zipIexecOutPath.isEmpty()) {
            return PostComputeResponse.builder()
                    .exitCause(ReplicateStatusCause.POST_COMPUTE_OUT_FOLDER_ZIP_FAILED)
                    .build();
        }
        // copy /output/iexec_out/computed.json to /output/computed.json as in FlowService#sendComputedFileToHost
        // return a POST_COMPUTE_SEND_COMPUTED_FILE_FAILED error on failure
        boolean isCopied = FileHelper.copyFile(
                taskIexecOutDir + IexecFileHelper.SLASH_COMPUTED_JSON,
                taskOutputDir + IexecFileHelper.SLASH_COMPUTED_JSON);
        if (!isCopied) {
            log.error("Failed to copy computed.json file to /output [chainTaskId:{}]", chainTaskId);
            return PostComputeResponse.builder()
                    .exitCause(ReplicateStatusCause.POST_COMPUTE_SEND_COMPUTED_FILE_FAILED)
                    .build();
        }
        return PostComputeResponse.builder().build();
    }

    Optional<ReplicateStatusCause> checkResultFilesName(String taskId, String iexecOutPath) {
        final AtomicBoolean failed = new AtomicBoolean(false);
        try {
            Files.walkFileTree(Paths.get(iexecOutPath), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    final String fileName = file.getFileName().toString();
                    if (fileName.length() > RESULT_FILE_NAME_MAX_LENGTH) {
                        log.error("Too long result file name [chainTaskId:{}, file:{}]", taskId, file);
                        failed.set(true);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Can't check result files [chainTaskId:{}]", taskId);
            return Optional.of(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
        }

        if (failed.get()) {
            return Optional.of(POST_COMPUTE_TOO_LONG_RESULT_FILE_NAME);
        }
        return Optional.empty();
    }

    public PostComputeResponse runTeePostCompute(TaskDescription taskDescription,
                                                 TeeSessionGenerationResponse secureSession) {
        String chainTaskId = taskDescription.getChainTaskId();

        TeeServicesProperties properties =
                teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);

        final TeeAppProperties postComputeProperties = properties.getPostComputeProperties();
        String postComputeImage = postComputeProperties.getImage();
        if (!dockerService.getClient().isImagePresent(postComputeImage)) {
            log.error("Tee post-compute image not found locally [chainTaskId:{}]",
                    chainTaskId);
            return PostComputeResponse.builder()
                    .exitCause(ReplicateStatusCause.POST_COMPUTE_IMAGE_MISSING)
                    .build();
        }
        TeeService teeService = teeServicesManager.getTeeService(taskDescription.getTeeFramework());
        List<String> env = teeService
                .buildPostComputeDockerEnv(taskDescription, secureSession);
        List<Bind> binds = Stream.of(
                        Collections.singletonList(dockerService.getIexecOutBind(chainTaskId)),
                        teeService.getAdditionalBindings())
                .flatMap(Collection::stream)
                .map(Bind::parse)
                .collect(Collectors.toList());

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(binds)
                .withDevices(sgxService.getSgxDevices())
                .withNetworkMode(workerConfigService.getDockerNetworkName());
        DockerRunRequest request = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .chainTaskId(chainTaskId)
                .containerName(getTaskTeePostComputeContainerName(chainTaskId))
                .imageUri(postComputeImage)
                .entrypoint(postComputeProperties.getEntrypoint())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .build();
        DockerRunResponse dockerResponse = dockerService.run(request);
        final DockerRunFinalStatus finalStatus = dockerResponse.getFinalStatus();
        if (finalStatus == DockerRunFinalStatus.TIMEOUT) {
            log.error("Tee post-compute container timed out" +
                            " [chainTaskId:{}, maxExecutionTime:{}]",
                    chainTaskId, taskDescription.getMaxExecutionTime());
            return PostComputeResponse.builder()
                    .exitCause(ReplicateStatusCause.POST_COMPUTE_TIMEOUT)
                    .build();
        }
        if (finalStatus == DockerRunFinalStatus.FAILED) {
            int exitCode = dockerResponse.getContainerExitCode();
            ReplicateStatusCause exitCause = getExitCause(chainTaskId, exitCode);
            log.error("Failed to run tee post-compute [chainTaskId:{}, " +
                    "exitCode:{}, exitCause:{}]", chainTaskId, exitCode, exitCause);
            return PostComputeResponse.builder()
                    .exitCause(exitCause)
                    .build();
        }
        return PostComputeResponse.builder()
                .stdout(dockerResponse.getStdout())
                .stderr(dockerResponse.getStderr())
                .build();
    }

    private ReplicateStatusCause getExitCause(String chainTaskId, Integer exitCode) {
        ReplicateStatusCause cause = null;
        if (exitCode != null && exitCode != 0) {
            switch (exitCode) {
                case 1:
                    cause = computeExitCauseService.getPostComputeExitCauseAndPrune(chainTaskId);
                    break;
                case 2:
                    cause = ReplicateStatusCause.POST_COMPUTE_EXIT_REPORTING_FAILED;
                    break;
                case 3:
                    cause = ReplicateStatusCause.POST_COMPUTE_TASK_ID_MISSING;
                    break;
                default:
                    break;
            }
        }
        return cause;
    }

    private String getTaskTeePostComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-post-compute";
    }

}