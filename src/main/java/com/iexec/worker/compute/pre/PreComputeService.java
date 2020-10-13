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
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class PreComputeService {

    private final SmsService smsService;
    private final DataService dataService;
    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;

    public PreComputeService(
            SmsService smsService,
            WorkerConfigurationService workerConfigService,
            DataService dataService,
            DockerService dockerService
    ) {
        this.smsService = smsService;
        this.workerConfigService = workerConfigService;
        this.dataService = dataService;
        this.dockerService = dockerService;
    }

    public boolean runStandardPreCompute(TaskDescription taskDescription, WorkerpoolAuthorization workerpoolAuth) {
        String chainTaskId = taskDescription.getChainTaskId();
        // Why do we need smsService for standard compute??
        Optional<TaskSecrets> oTaskSecrets = smsService.fetchTaskSecrets(workerpoolAuth);
        if (oTaskSecrets.isEmpty()) {
            log.warn("No secrets fetched for this task, will continue [chainTaskId:{}]:", chainTaskId);
        } else {
            String datasetSecretFilePath = workerConfigService.getDatasetSecretFilePath(chainTaskId);
            String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);
            String enclaveSecretFilePath = workerConfigService.getEnclaveSecretFilePath(chainTaskId);
            smsService.saveSecrets(chainTaskId, oTaskSecrets.get(), datasetSecretFilePath,
                    beneficiarySecretFilePath, enclaveSecretFilePath);
        }
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
        if (!dockerService.pullImage(taskDescription.getTeePostComputeImage())) {
            log.error("Cannot pull TEE post compute image [chainTaskId:{}, imageUri:{}]",
                    chainTaskId, taskDescription.getTeePostComputeImage());
            return "";
        }

        String secureSessionId = smsService.createTeeSession(workerpoolAuth);
        if (secureSessionId.isEmpty()) {
            log.error("Cannot compute TEE task without secure session [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("Secure session created [chainTaskId:{}, secureSessionId:{}]", chainTaskId, secureSessionId);
        }
        return secureSessionId;
    }

}