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
import com.iexec.common.web.ApiResponseBodyDecoder;
import com.iexec.sms.api.*;
import com.iexec.sms.api.config.TeeServicesConfiguration;
import com.iexec.worker.chain.CredentialsService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class SmsService {

    private final CredentialsService credentialsService;
    private final SmsClientProvider smsClientProvider;

    public SmsService(CredentialsService credentialsService,
                      SmsClientProvider smsClientProvider) {
        this.credentialsService = credentialsService;
        this.smsClientProvider = smsClientProvider;
    }

    @Retryable(value = FeignException.class)
    public Optional<TaskSecrets> fetchTaskSecrets(WorkerpoolAuthorization workerpoolAuthorization) {
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        String authorization = getAuthorizationString(workerpoolAuthorization);
        SmsSecretResponse smsResponse;

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(chainTaskId);
        smsResponse = smsClient.getUnTeeSecrets(authorization, workerpoolAuthorization);

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
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, attempts:3]",
                workerpoolAuthorization.getChainTaskId(), e);
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

    // TODO: use the below method with retry.
    public TeeSessionGenerationResponse createTeeSession(WorkerpoolAuthorization workerpoolAuthorization) throws TeeSessionGenerationException {
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        log.info("Creating TEE session [chainTaskId:{}]", chainTaskId);
        String authorization = getAuthorizationString(workerpoolAuthorization);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(chainTaskId);

        try {
            TeeSessionGenerationResponse session = smsClient.generateTeeSession(authorization, workerpoolAuthorization)
                    .getData();
            log.info("Created TEE session [chainTaskId:{}, session:{}]",
                    chainTaskId, session);
            return session;
        } catch(FeignException e) {
            log.error("SMS failed to create TEE session [chainTaskId:{}]",
                    chainTaskId, e);
            final Optional<TeeSessionGenerationError> error = ApiResponseBodyDecoder.getErrorFromResponse(e.contentUTF8(), TeeSessionGenerationError.class);
            throw new TeeSessionGenerationException(error.orElse(TeeSessionGenerationError.UNKNOWN_ISSUE));
        }
    }

    /*
    * Don't retry createTeeSession for now, to avoid polluting logs in SMS & CAS
    * */
    // @Retryable(value = FeignException.class)
    // public String createTeeSession(WorkerpoolAuthorization workerpoolAuthorization) {
    //     String authorization = getAuthorizationString(workerpoolAuthorization);
    //     String response = smsClient.generateTeeSession(authorization, workerpoolAuthorization);
    //     log.info("Response of createTeeSession [chainTaskId:{}, httpBody:{}]",
    //             workerpoolAuthorization.getChainTaskId(), response);
    //     return response;
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
