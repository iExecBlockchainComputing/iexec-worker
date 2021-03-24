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
import com.iexec.common.precompute.PreComputeExitCode;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeLasConfiguration;
import com.iexec.worker.tee.scone.SconeTeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
public class PreComputeService {

    private final SmsService smsService;
    private final DataService dataService;
    private final DockerService dockerService;
    private final SconeTeeService sconeTeeService;
    private final SconeLasConfiguration sconeLasConfiguration;
    private final WorkerConfigurationService workerConfigService;

    public PreComputeService(
            SmsService smsService,
            DataService dataService,
            DockerService dockerService,
            SconeTeeService sconeTeeService,
            SconeLasConfiguration sconeLasConfiguration,
            WorkerConfigurationService workerConfigService
    ) {
        this.smsService = smsService;
        this.dataService = dataService;
        this.dockerService = dockerService;
        this.sconeTeeService = sconeTeeService;
        this.sconeLasConfiguration = sconeLasConfiguration;
        this.workerConfigService = workerConfigService;
    }

    public boolean runStandardPreCompute(TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();
        boolean isDatasetDecryptionNeeded = dataService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;
        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = dataService.decryptDataset(chainTaskId, taskDescription.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            log.error("Failed to decrypt dataset [chainTaskId:{}, uri:{}]",
                    chainTaskId, taskDescription.getDatasetUri());
            return false;
        }
        return true;
    }


    public String runTeePreCompute(TaskDescription taskDescription, WorkerpoolAuthorization workerpoolAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
        // download pre-compute image
        String preComputeImageUri = smsService.getPreComputeImageUri();
        if (preComputeImageUri.isEmpty()) {
            log.error("Failed to get TEE pre-compute image URI [chainTaskId:{}]", chainTaskId);
            return "";
        }
        if (!dockerService.getClient().pullImage(preComputeImageUri)) {
            log.error("Failed to pull TEE pre-compute image [chainTaskId:{}]", chainTaskId);
            return "";
        }
        // download post-compute image
        if (!dockerService.getClient().pullImage(taskDescription.getTeePostComputeImage())) {
            log.error("Cannot pull TEE post compute image [chainTaskId:{}, imageUri:{}]",
                    chainTaskId, taskDescription.getTeePostComputeImage());
            return "";
        }
        // create secure session
        String secureSessionId = smsService.createTeeSession(workerpoolAuth);
        if (secureSessionId.isEmpty()) {
            log.error("Failed to create TEE secure session [chainTaskId:{}]", chainTaskId);
            return "";
        }
        log.info("Created secure session [chainTaskId:{}, secureSessionId:{}]", chainTaskId, secureSessionId);
        // run pre-compute container
        List<String> env = sconeTeeService.getPreComputeDockerEnv(secureSessionId);
        List<String> binds = List.of(dockerService.getIexecInBind(chainTaskId));
        DockerRunRequest request = DockerRunRequest.builder()
                .chainTaskId(chainTaskId)
                .containerName(getTeePreComputeContainerName(chainTaskId))
                .imageUri(preComputeImageUri)
                .maxExecutionTime(taskDescription.getMaxExecutionTime())
                .env(env)
                .binds(binds)
                .isSgx(true)
                .dockerNetwork(sconeLasConfiguration.getDockerNetworkName())
                .shouldDisplayLogs(taskDescription.isDeveloperLoggerEnabled())
                .build();
        DockerRunResponse dockerResponse = dockerService.run(request);
        int exitCodeValue = dockerResponse.getContainerExitCode();
        PreComputeExitCode exitCodeName = PreComputeExitCode.nameOf(exitCodeValue); // can be null
        if (!dockerResponse.isSuccessful()) {
            log.error("Failed to run pre-compute container [chainTaskId:{}, " +
                    "exitCode:{}, error:{}]", chainTaskId, exitCodeValue, exitCodeName);
            return "";
        }
        if (!PreComputeExitCode.isSuccess(exitCodeValue)) {
            log.error("Pre-compute container exited with error code [chainTaskId:{}, " +
                    "exitCode:{}, error:{}", chainTaskId, exitCodeValue, exitCodeName);
        }
        return secureSessionId;
    }

    private String getTeePreComputeContainerName(String chainTaskId) {
        return workerConfigService.getWorkerName() + "-" + chainTaskId + "-tee-pre-compute";
    }
}