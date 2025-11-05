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

package com.iexec.worker.compute.pre;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.ComputeStage;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import com.iexec.worker.workflow.WorkflowError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class PreComputeService {

    private final DockerService dockerService;
    private final TeeServicesManager teeServicesManager;
    private final WorkerConfigurationService workerConfigService;
    private final ComputeExitCauseService computeExitCauseService;
    private final TeeServicesPropertiesService teeServicesPropertiesService;
    private final ComputeDurationsService preComputeDurationsService;

    public PreComputeService(final DockerService dockerService,
                             final TeeServicesManager teeServicesManager,
                             final WorkerConfigurationService workerConfigService,
                             final ComputeExitCauseService computeExitCauseService,
                             final TeeServicesPropertiesService teeServicesPropertiesService,
                             final ComputeDurationsService preComputeDurationsService) {
        this.dockerService = dockerService;
        this.teeServicesManager = teeServicesManager;
        this.workerConfigService = workerConfigService;
        this.computeExitCauseService = computeExitCauseService;
        this.teeServicesPropertiesService = teeServicesPropertiesService;
        this.preComputeDurationsService = preComputeDurationsService;
    }

    /**
     * Check the heap size of the application enclave and create a new tee session.
     * If a user specific post-compute is specified it will be downloaded.
     * If the task contains a dataset or some input files, the pre-compute enclave
     * is started to handle them.
     *
     * @param taskDescription Task description read on-chain
     * @return PreComputeResponse
     */
    public PreComputeResponse runTeePreCompute(final TaskDescription taskDescription) {
        final String chainTaskId = taskDescription.getChainTaskId();
        final PreComputeResponse.PreComputeResponseBuilder preComputeResponseBuilder = PreComputeResponse.builder();

        // run TEE pre-compute container if needed
        if (taskDescription.requiresPreCompute()) {
            log.info("Task contains TEE input data [chainTaskId:{}, containsDataset:{}, containsInputFiles:{}, isBulkRequest:{}]",
                    chainTaskId, taskDescription.containsDataset(), taskDescription.containsInputFiles(), taskDescription.isBulkRequest());
            final List<WorkflowError> exitCauses = downloadDatasetAndFiles(taskDescription);
            preComputeResponseBuilder.exitCauses(exitCauses);
        }

        return preComputeResponseBuilder.build();
    }

    private List<WorkflowError> downloadDatasetAndFiles(final TaskDescription taskDescription) {
        try {
            final Integer exitCode = prepareTeeInputData(taskDescription);
            if (exitCode == null || exitCode != 0) {
                final String chainTaskId = taskDescription.getChainTaskId();
                final List<WorkflowError> exitCauses = getExitCauses(chainTaskId, exitCode);
                log.error("Failed to prepare TEE input data [chainTaskId:{}, exitCode:{}, exitCauses:{}]",
                        chainTaskId, exitCode, exitCauses);
                return exitCauses;
            }
        } catch (TimeoutException e) {
            return List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_TIMEOUT));
        }
        return List.of();
    }

    private List<WorkflowError> getExitCauses(final String chainTaskId, final Integer exitCode) {
        if (exitCode == null) {
            return List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_IMAGE_MISSING));
        }
        return switch (exitCode) {
            case 1 -> computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(
                    chainTaskId, ComputeStage.PRE, new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE));
            case 2 -> List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_EXIT_REPORTING_FAILED));
            case 3 -> List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_TASK_ID_MISSING));
            default -> List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE));
        };
    }

    /**
     * Run tee-worker-pre-compute docker image. The pre-compute enclave downloads
     * the dataset and decrypts it for the compute stage. It also downloads input
     * files if requested.
     *
     * @return pre-compute exit code
     */
    private Integer prepareTeeInputData(final TaskDescription taskDescription) throws TimeoutException {
        final String chainTaskId = taskDescription.getChainTaskId();
        log.info("Preparing tee input data [chainTaskId:{}]", chainTaskId);

        final TeeServicesProperties properties = teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);

        // check that docker image is present
        final TeeAppProperties preComputeProperties = properties.getPreComputeProperties();
        final String preComputeImage = preComputeProperties.getImage();
        if (!dockerService.getClient().isImagePresent(preComputeImage)) {
            log.error("Tee pre-compute image not found locally [chainTaskId:{}]", chainTaskId);
            return null;
        }
        // run container
        final TeeService teeService = teeServicesManager.getTeeService(taskDescription.getTeeFramework());
        final List<String> env = teeService.buildPreComputeDockerEnv(taskDescription);
        final List<Bind> binds = List.of(Bind.parse(dockerService.getInputBind(chainTaskId)));
        final HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(binds)
                .withDevices(teeService.getDevices())
                .withNetworkMode(workerConfigService.getDockerNetworkName());
        final DockerRunRequest request = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .chainTaskId(chainTaskId)
                .containerName(getTeePreComputeContainerName(chainTaskId))
                .imageUri(preComputeImage)
                .entrypoint(preComputeProperties.getEntrypoint())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .build();
        final DockerRunResponse dockerResponse = dockerService.run(request);
        final Duration executionDuration = dockerResponse.getExecutionDuration();
        if (executionDuration != null) {
            preComputeDurationsService.addDurationForTask(chainTaskId, executionDuration.toMillis());
        }
        final DockerRunFinalStatus finalStatus = dockerResponse.getFinalStatus();
        if (finalStatus == DockerRunFinalStatus.TIMEOUT) {
            log.error("Tee pre-compute container timed out [chainTaskId:{}, maxExecutionTime:{}]",
                    chainTaskId, taskDescription.getMaxExecutionTime());
            throw new TimeoutException("Tee pre-compute container timed out");
        }
        if (finalStatus == DockerRunFinalStatus.FAILED) {
            int exitCode = dockerResponse.getContainerExitCode();
            log.error("Tee pre-compute container failed [chainTaskId:{}, exitCode:{}]",
                    chainTaskId, exitCode);
            return dockerResponse.getContainerExitCode();
        }
        log.info("Prepared tee input data successfully [chainTaskId:{}]", chainTaskId);
        return 0;
    }

    private String getTeePreComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-pre-compute";
    }
}
