package com.iexec.worker.sms;

import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.sms.secret.SmsSecret;
import com.iexec.common.sms.secret.SmsSecretResponse;
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.SmsClient;

import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SmsService {

    private CredentialsService credentialsService;
    private SmsClient smsClient;

    public SmsService(CredentialsService credentialsService, SmsClient smsClient) {
        this.credentialsService = credentialsService;
        this.smsClient = smsClient;
    }

    @Retryable(value = FeignException.class)
    public Optional<TaskSecrets> fetchTaskSecrets(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        String authorization = getAuthorizationString(contributionAuth);
        ResponseEntity<SmsSecretResponse> response = smsClient.getUnTeeSecrets(authorization, contributionAuth);
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
    private Optional<TaskSecrets> fetchTaskSecrets(FeignException e, ContributionAuthorization contributionAuth) {
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, httpStatus:{}, exception:{}, attempts:3]",
                contributionAuth.getChainTaskId(), e.status(), e.getMessage());
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

    @Retryable(value = FeignException.class)
    public String createTeeSession(ContributionAuthorization contributionAuth) {
        String authorization = getAuthorizationString(contributionAuth);
        ResponseEntity<String> response = smsClient.createTeeSession(authorization, contributionAuth);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : "";
    }

    @Recover
    private String createTeeSession(FeignException e, ContributionAuthorization contributionAuth) {
        log.error("Failed to create secure session [chainTaskId:{}, httpStatus:{}, exception:{}, attempts:3]",
                contributionAuth.getChainTaskId(), e.status(), e.getMessage());
        return "";
    }

    private String getAuthorizationString(ContributionAuthorization contributionAuth) {
        String challenge = contributionAuth.getHash();
        return credentialsService.hashAndSignMessage(challenge).getValue();
    }
}
