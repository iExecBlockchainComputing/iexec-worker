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

package com.iexec.worker.sms;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.sms.secret.SmsSecret;
import com.iexec.common.sms.secret.SmsSecretResponse;
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.SmsClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class SmsService {

    private final CredentialsService credentialsService;
    private final SmsClient smsClient;

    public SmsService(CredentialsService credentialsService, SmsClient smsClient) {
        this.credentialsService = credentialsService;
        this.smsClient = smsClient;
    }

    @Retryable(value = FeignException.class)
    public Optional<TaskSecrets> fetchTaskSecrets(WorkerpoolAuthorization workerpoolAuthorization) {
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        String authorization = getAuthorizationString(workerpoolAuthorization);
        ResponseEntity<SmsSecretResponse> response = smsClient.getUnTeeSecrets(authorization, workerpoolAuthorization);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Optional.empty();
        }

        SmsSecretResponse smsResponse = response.getBody();
        if (smsResponse == null) {
            log.error("Received null response from SMS [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        if (!smsResponse.isOk()) {
            log.error("An error occurred while getting task secrets [chainTaskId:{}, errorMessage:{}]",
                    chainTaskId, smsResponse.getErrorMessage());
            return Optional.empty();
        }

        TaskSecrets taskSecrets = smsResponse.getData().getSecrets();

        if (taskSecrets == null) {
            log.error("Received null secrets object from SMS [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        return Optional.of(taskSecrets);
    }

    @Recover
    private Optional<TaskSecrets> fetchTaskSecrets(FeignException e, WorkerpoolAuthorization workerpoolAuthorization) {
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, httpStatus:{}, exception:{}, attempts:3]",
                workerpoolAuthorization.getChainTaskId(), e.status(), e.getMessage());
        return Optional.empty();
    }

    public void saveSecrets(String chainTaskId,
                            TaskSecrets taskSecrets,
                            String datasetSecretFilePath,
                            String beneficiarySecretFilePath,
                            String enclaveSecretFilePath) {

        SmsSecret datasetSecret = taskSecrets.getDatasetSecret();
        SmsSecret beneficiarySecret = taskSecrets.getBeneficiarySecret();
        SmsSecret enclaveSecret = taskSecrets.getEnclaveSecret();

        if (datasetSecret != null && datasetSecret.getSecret() != null && !datasetSecret.getSecret().isEmpty()) {
            FileHelper.createFileWithContent(datasetSecretFilePath, datasetSecret.getSecret() + "\n");
            log.info("Saved dataset secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No dataset secret for this task [chainTaskId:{}]", chainTaskId);
        }

        if (beneficiarySecret != null && beneficiarySecret.getSecret() != null && !beneficiarySecret.getSecret().isEmpty()) {
            FileHelper.createFileWithContent(beneficiarySecretFilePath, beneficiarySecret.getSecret());
            log.info("Saved beneficiary secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No beneficiary secret for this task [chainTaskId:{}]", chainTaskId);
        }

        if (enclaveSecret != null && enclaveSecret.getSecret() != null && !enclaveSecret.getSecret().isEmpty()) {
            FileHelper.createFileWithContent(enclaveSecretFilePath, enclaveSecret.getSecret());
            log.info("Saved enclave secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No enclave secret for this task [chainTaskId:{}]", chainTaskId);
        }
    }

    public String getPreComputeImageUri() {
        try {
            ResponseEntity<String> response = smsClient.getPreComputeImageUri();
            return response.getStatusCode().is2xxSuccessful()
                    ? response.getBody()
                    : "";
        } catch (Exception e) {
            log.error("Failed to get pre-compute image URI from sms", e);
            return "";
        }
    }

    // TODO: use the below method with retry.
    public String createTeeSession(WorkerpoolAuthorization workerpoolAuthorization) {
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        log.info("Creating TEE session [chainTaskId:{}]", chainTaskId);
        String authorization = getAuthorizationString(workerpoolAuthorization);
        try {
            ResponseEntity<String> response = smsClient
                    .createTeeSession(authorization, workerpoolAuthorization);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("SMS failed to create TEE session [chainTaskId:{}, " +
                        "httpStatus:{}]", chainTaskId, response.getStatusCode());
                return "";
            }
            String sessionId = response.getBody();
            log.info("Created TEE session [chainTaskId:{}, sessionId:{}]",
                    chainTaskId, sessionId);
            return sessionId;
        } catch (Exception e) {
            log.error("SMS failed to create TEE session [chainTaskId:{}]",
                    chainTaskId, e);
            return "";
        }
    }

    /*
    * Don't retry createTeeSession for now, to avoid polluting logs in SMS & CAS
    * */
    // @Retryable(value = FeignException.class)
    // public String createTeeSession(WorkerpoolAuthorization workerpoolAuthorization) {
    //     String authorization = getAuthorizationString(workerpoolAuthorization);
    //     ResponseEntity<String> response = smsClient.createTeeSession(authorization, workerpoolAuthorization);
    //     log.info("Response of createTeeSession [chainTaskId:{}, httpStatus:{}, httpBody:{}]",
    //             workerpoolAuthorization.getChainTaskId(), response.getStatusCode(), response.getBody());
    //     return response.getStatusCode().is2xxSuccessful() ? response.getBody() : "";
    // }

    // @Recover
    // private String createTeeSession(FeignException e, WorkerpoolAuthorization workerpoolAuthorization) {
    //     log.error("Failed to create secure session [chainTaskId:{}, httpStatus:{}, exception:{}, attempts:3]",
    //             workerpoolAuthorization.getChainTaskId(), e.status(), e.getMessage());
    //     return "";
    // }

    private String getAuthorizationString(WorkerpoolAuthorization workerpoolAuthorization) {
        String challenge = workerpoolAuthorization.getHash();
        return credentialsService.hashAndSignMessage(challenge).getValue();
    }
}
