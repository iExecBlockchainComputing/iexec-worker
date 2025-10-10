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
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.ComputeStage;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.metric.ComputeDurationsService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import com.iexec.worker.workflow.WorkflowError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.sms.api.TeeSessionGenerationError.UNKNOWN_ISSUE;

@Slf4j
@Service
public class PreComputeService {

    private final SmsService smsService;
    private final DockerService dockerService;
    private final TeeServicesManager teeServicesManager;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final ComputeExitCauseService computeExitCauseService;
    private final TeeServicesPropertiesService teeServicesPropertiesService;
    private final ComputeDurationsService preComputeDurationsService;

    public PreComputeService(
            SmsService smsService,
            DockerService dockerService,
            TeeServicesManager teeServicesManager,
            WorkerConfigurationService workerConfigService,
            SgxService sgxService,
            ComputeExitCauseService computeExitCauseService,
            TeeServicesPropertiesService teeServicesPropertiesService,
            ComputeDurationsService preComputeDurationsService) {
        this.smsService = smsService;
        this.dockerService = dockerService;
        this.teeServicesManager = teeServicesManager;
        this.workerConfigService = workerConfigService;
        this.sgxService = sgxService;
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
     * @param workerpoolAuth  Workerpool authorization provided by scheduler
     * @return PreComputeResponse
     */
    public PreComputeResponse runTeePreCompute(TaskDescription taskDescription, WorkerpoolAuthorization workerpoolAuth) {
        final String chainTaskId = taskDescription.getChainTaskId();
        final PreComputeResponse.PreComputeResponseBuilder preComputeResponseBuilder = PreComputeResponse.builder()
                .isTeeTask(taskDescription.isTeeTask());

        // verify enclave configuration for compute stage
        final TeeEnclaveConfiguration enclaveConfig = taskDescription.getAppEnclaveConfiguration();
        if (enclaveConfig == null) {
            log.error("No enclave configuration found for task [chainTaskId:{}]", chainTaskId);
            return preComputeResponseBuilder
                    .exitCauses(List.of(WorkflowError.builder()
                            .cause(PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION).build()))
                    .build();
        }
        if (!enclaveConfig.getValidator().isValid()) {
            log.error("Invalid enclave configuration [chainTaskId:{}, violations:{}]",
                    chainTaskId, enclaveConfig.getValidator().validate().toString());
            return preComputeResponseBuilder
                    .exitCauses(List.of(WorkflowError.builder()
                            .cause(PRE_COMPUTE_INVALID_ENCLAVE_CONFIGURATION).build()))
                    .build();
        }
        long teeComputeMaxHeapSize = DataSize
                .ofGigabytes(workerConfigService.getTeeComputeMaxHeapSizeGb())
                .toBytes();
        if (enclaveConfig.getHeapSize() > teeComputeMaxHeapSize) {
            log.error("Enclave configuration should define a proper heap size [chainTaskId:{}, heapSize:{}, maxHeapSize:{}]",
                    chainTaskId, enclaveConfig.getHeapSize(), teeComputeMaxHeapSize);
            preComputeResponseBuilder.exitCauses(List.of(WorkflowError.builder()
                    .cause(PRE_COMPUTE_INVALID_ENCLAVE_HEAP_CONFIGURATION).build()));
            return preComputeResponseBuilder.build();
        }
        // create secure session
        final TeeSessionGenerationResponse secureSession;
        try {
            secureSession = smsService.createTeeSession(workerpoolAuth);
            if (secureSession == null) {
                throw new TeeSessionGenerationException(UNKNOWN_ISSUE);
            }
            preComputeResponseBuilder.secureSession(secureSession);
        } catch (TeeSessionGenerationException e) {
            log.error("Failed to create TEE secure session [chainTaskId:{}]", chainTaskId, e);
            return preComputeResponseBuilder
                    .exitCauses(List.of(WorkflowError.builder()
                            .cause(teeSessionGenerationErrorToReplicateStatusCause(e.getTeeSessionGenerationError())).build()))
                    .build();
        }

        // run TEE pre-compute container if needed
        if (taskDescription.requiresPreCompute()) {
            log.info("Task contains TEE input data [chainTaskId:{}, containsDataset:{}, containsInputFiles:{}, isBulkRequest:{}]",
                    chainTaskId, taskDescription.containsDataset(), taskDescription.containsInputFiles(), taskDescription.isBulkRequest());
            final List<WorkflowError> exitCauses = downloadDatasetAndFiles(taskDescription, secureSession);
            preComputeResponseBuilder.exitCauses(exitCauses);
        }

        return preComputeResponseBuilder.build();
    }

    private List<WorkflowError> downloadDatasetAndFiles(
            final TaskDescription taskDescription,
            final TeeSessionGenerationResponse secureSession) {
        try {
            final Integer exitCode = prepareTeeInputData(taskDescription, secureSession);
            if (exitCode == null || exitCode != 0) {
                final String chainTaskId = taskDescription.getChainTaskId();
                final List<WorkflowError> exitCauses = getExitCauses(chainTaskId, exitCode);
                log.error("Failed to prepare TEE input data [chainTaskId:{}, exitCode:{}, exitCauses:{}]",
                        chainTaskId, exitCode, exitCauses);
                return exitCauses;
            }
        } catch (TimeoutException e) {
            return List.of(WorkflowError.builder()
                    .cause(PRE_COMPUTE_TIMEOUT).build());
        }
        return List.of();
    }

    private List<WorkflowError> getExitCauses(final String chainTaskId, final Integer exitCode) {
        if (exitCode == null) {
            return List.of(WorkflowError.builder()
                    .cause(PRE_COMPUTE_IMAGE_MISSING).build());
        }
        return switch (exitCode) {
            case 1 -> computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(
                    chainTaskId, ComputeStage.PRE, WorkflowError.builder().cause(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE).build());
            case 2 -> List.of(WorkflowError.builder().cause(PRE_COMPUTE_EXIT_REPORTING_FAILED).build());
            case 3 -> List.of(WorkflowError.builder().cause(PRE_COMPUTE_TASK_ID_MISSING).build());
            default -> List.of(WorkflowError.builder().cause(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE).build());
        };
    }


    /**
     * {@link TeeSessionGenerationError} and {@link ReplicateStatusCause} are dynamically bound
     * such as {@code TeeSessionGenerationError.MEMBER_X == ReplicateStatusCause.TEE_SESSION_GENERATION_MEMBER_X}.
     *
     * @return {@literal null} if no member of {@link ReplicateStatusCause} matches,
     * the matching member otherwise.
     */
    ReplicateStatusCause teeSessionGenerationErrorToReplicateStatusCause(TeeSessionGenerationError error) {
        try {
            return ReplicateStatusCause.valueOf("TEE_SESSION_GENERATION_" + error.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Run tee-worker-pre-compute docker image. The pre-compute enclave downloads
     * the dataset and decrypts it for the compute stage. It also downloads input
     * files if requested.
     *
     * @return pre-compute exit code
     */
    private Integer prepareTeeInputData(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse secureSession)
            throws TimeoutException {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Preparing tee input data [chainTaskId:{}]", chainTaskId);

        TeeServicesProperties properties =
                teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);

        // check that docker image is present
        final TeeAppProperties preComputeProperties = properties.getPreComputeProperties();
        String preComputeImage = preComputeProperties.getImage();
        if (!dockerService.getClient().isImagePresent(preComputeImage)) {
            log.error("Tee pre-compute image not found locally [chainTaskId:{}]", chainTaskId);
            return null;
        }
        // run container
        List<String> env = teeServicesManager.getTeeService(taskDescription.getTeeFramework())
                .buildPreComputeDockerEnv(taskDescription, secureSession);
        List<Bind> binds = Collections.singletonList(Bind.parse(dockerService.getInputBind(chainTaskId)));
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(binds)
                .withDevices(sgxService.getSgxDevices())
                .withNetworkMode(workerConfigService.getDockerNetworkName());
        DockerRunRequest request = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .chainTaskId(chainTaskId)
                .containerName(getTeePreComputeContainerName(chainTaskId))
                .imageUri(preComputeImage)
                .entrypoint(preComputeProperties.getEntrypoint())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .build();
        DockerRunResponse dockerResponse = dockerService.run(request);
        final Duration executionDuration = dockerResponse.getExecutionDuration();
        if (executionDuration != null) {
            preComputeDurationsService.addDurationForTask(chainTaskId, executionDuration.toMillis());
        }
        final DockerRunFinalStatus finalStatus = dockerResponse.getFinalStatus();
        if (finalStatus == DockerRunFinalStatus.TIMEOUT) {
            log.error("Tee pre-compute container timed out" +
                            " [chainTaskId:{}, maxExecutionTime:{}]",
                    chainTaskId, taskDescription.getMaxExecutionTime());
            throw new TimeoutException("Tee pre-compute container timed out");
        }
        if (finalStatus == DockerRunFinalStatus.FAILED) {
            int exitCode = dockerResponse.getContainerExitCode();
            log.error("Tee pre-compute container failed [chainTaskId:{}, " +
                    "exitCode:{}]", chainTaskId, exitCode);
            return dockerResponse.getContainerExitCode();
        }
        log.info("Prepared tee input data successfully [chainTaskId:{}]", chainTaskId);
        return 0;
    }

    private String getTeePreComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-pre-compute";
    }
}
