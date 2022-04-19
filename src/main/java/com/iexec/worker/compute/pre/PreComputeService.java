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

package com.iexec.worker.compute.pre;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.worker.compute.ComputeExitCauseService;
import com.iexec.worker.compute.TeeWorkflowConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.TeeSconeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class PreComputeService {

    private final SmsService smsService;
    private final DockerService dockerService;
    private final TeeSconeService teeSconeService;
    private final WorkerConfigurationService workerConfigService;
    private final TeeWorkflowConfiguration teeWorkflowConfig;
    private final SgxService sgxService;
    private final ComputeExitCauseService computeExitCauseService;

    public PreComputeService(
            SmsService smsService,
            DockerService dockerService,
            TeeSconeService teeSconeService,
            WorkerConfigurationService workerConfigService,
            TeeWorkflowConfiguration teeWorkflowConfig,
            SgxService sgxService,
            ComputeExitCauseService computeExitCauseService) {
        this.smsService = smsService;
        this.dockerService = dockerService;
        this.teeSconeService = teeSconeService;
        this.workerConfigService = workerConfigService;
        this.teeWorkflowConfig = teeWorkflowConfig;
        this.sgxService = sgxService;
        this.computeExitCauseService = computeExitCauseService;
    }

    /**
     * Check the heap size of the application enclave and create a new tee session.
     * If a user specific post-compute is specified it will be downloaded.
     * If the task contains a dataset or some input files, the pre-compute enclave
     * is started to handle them.
     *
     * @param taskDescription
     * @param workerpoolAuth
     * @return created tee session id if success, empty string otherwise
     */
    public PreComputeResponse runTeePreCompute(TaskDescription taskDescription, WorkerpoolAuthorization workerpoolAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
        PreComputeResponse preComputeResponse = PreComputeResponse.builder()
                .isTeeTask(taskDescription.isTeeTask())
                .build();
        // verify enclave configuration for compute stage
        TeeEnclaveConfiguration enclaveConfig = taskDescription.getAppEnclaveConfiguration();
        if (!enclaveConfig.getValidator().isValid()) {
            log.error("Invalid enclave configuration [chainTaskId:{}, violations:{}]",
                    chainTaskId, enclaveConfig.getValidator().validate().toString());
            return preComputeResponse;
        }
        long teeComputeMaxHeapSize = DataSize
                .ofGigabytes(workerConfigService.getTeeComputeMaxHeapSizeGb())
                .toBytes();
        if (enclaveConfig.getHeapSize() > teeComputeMaxHeapSize) {
            log.error("Enclave configuration should define a proper heap " +
                            "size [chainTaskId:{}, heapSize:{}, maxHeapSize:{}]",
                    chainTaskId, enclaveConfig.getHeapSize(), teeComputeMaxHeapSize);
            return preComputeResponse;
        }
        // ###############################################################################
        // TODO: activate this when user specific post-compute is properly
        // supported. See https://github.com/iExecBlockchainComputing/iexec-sms/issues/52.
        // ###############################################################################
        // Download specific post-compute image if requested.
        // Otherwise the default one will be used. 
        // if (taskDescription.containsPostCompute() &&
        //         !dockerService.getClient()
        //                 .pullImage(taskDescription.getTeePostComputeImage())) {
        //     log.error("Failed to pull specified tee post-compute image " +
        //             "[chainTaskId:{}, imageUri:{}]", chainTaskId,
        //             taskDescription.getTeePostComputeImage());
        //     return "";
        // }
        // ###############################################################################
        // create secure session
        String secureSessionId = smsService.createTeeSession(workerpoolAuth);
        if (secureSessionId.isEmpty()) {
            log.error("Failed to create TEE secure session [chainTaskId:{}]", chainTaskId);
            return preComputeResponse;
        }
        // run TEE pre-compute container if needed
        if (taskDescription.containsDataset() ||
                taskDescription.containsInputFiles()) {
            log.info("Task contains TEE input data [chainTaskId:{}, containsDataset:{}, " +
                            "containsInputFiles:{}]", chainTaskId, taskDescription.containsDataset(),
                    taskDescription.containsInputFiles());
            Integer exitCode = prepareTeeInputData(taskDescription, secureSessionId);
            if (exitCode == null || exitCode != 0) {
                ReplicateStatusCause exitCause = getExitCause(chainTaskId, exitCode);
                log.error("Failed to prepare TEE input data [chainTaskId:{}, " +
                        "exitCode:{}, exitCause:{}]", chainTaskId, exitCode, exitCause);
                preComputeResponse.setExitCause(exitCause);
                return preComputeResponse;
            }
        }
        preComputeResponse.setSecureSessionId(secureSessionId);
        return preComputeResponse;
    }

    private ReplicateStatusCause getExitCause(String chainTaskId, Integer exitCode) {
        ReplicateStatusCause cause = null;
        if (exitCode != null && exitCode != 0) {
            switch (exitCode) {
                case 1:
                    cause = computeExitCauseService.getPreComputeExitCause(chainTaskId);
                    break;
                case 2:
                    cause = ReplicateStatusCause.PRE_COMPUTE_EXIT_REPORTING_FAILED;
                    break;
                case 3:
                    cause = ReplicateStatusCause.PRE_COMPUTE_TASK_ID_MISSING;
                    break;
                default:
                    break;
            }
        }
        return cause;
    }

    /**
     * Run tee-worker-pre-compute docker image. The pre-compute enclave downloads
     * the dataset and decrypts it for the compute stage. It also downloads input
     * files if requested.
     *
     * @param taskDescription
     * @param secureSessionId
     * @return true if input data was successfully prepared, false if a
     * problem occurs.
     */
    private Integer prepareTeeInputData(TaskDescription taskDescription, String secureSessionId) {
        String chainTaskId = taskDescription.getChainTaskId();
        log.info("Preparing tee input data [chainTaskId:{}]", chainTaskId);
        // check that docker image is present
        String preComputeImage = teeWorkflowConfig.getPreComputeImage();
        long preComputeHeapSize = teeWorkflowConfig.getPreComputeHeapSize();
        if (!dockerService.getClient().isImagePresent(preComputeImage)) {
            log.error("Tee pre-compute image not found locally [chainTaskId:{}]", chainTaskId);
            return null;
        }
        // run container
        List<String> env = teeSconeService.buildPreComputeDockerEnv(secureSessionId,
                preComputeHeapSize);
        List<String> binds = Collections.singletonList(dockerService.getInputBind(chainTaskId));
        DockerRunRequest request = DockerRunRequest.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTeePreComputeContainerName(chainTaskId))
                .imageUri(preComputeImage)
                .entrypoint(teeWorkflowConfig.getPreComputeEntrypoint())
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .binds(binds)
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .dockerNetwork(workerConfigService.getDockerNetworkName())
                .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                .build();
        DockerRunResponse dockerResponse = dockerService.run(request);
        if (!dockerResponse.isSuccessful()) {
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