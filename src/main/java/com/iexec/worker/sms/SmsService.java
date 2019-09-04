package com.iexec.worker.sms;

import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.sms.secrets.SmsSecret;
import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.SmsRequestData;
import com.iexec.common.sms.scone.SconeSecureSessionResponse.SconeSecureSession;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.common.sms.secrets.TaskSecrets;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.SmsClient;
import com.iexec.worker.utils.FileHelper;

import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SmsService {

    private SmsClient smsClient;
    private CredentialsService credentialsService;

    public SmsService(CredentialsService credentialsService, SmsClient smsClient) {
        this.smsClient = smsClient;
        this.credentialsService = credentialsService;
    }

    @Retryable(value = FeignException.class)
    public Optional<TaskSecrets> fetchTaskSecrets(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();

        SmsRequest smsRequest = buildSmsRequest(contributionAuth);
        SmsSecretResponse smsResponse = smsClient.getUnTeeSecrets(smsRequest);

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
    private boolean fetchTaskSecrets(FeignException e, ContributionAuthorization contributionAuth) {
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, attempts:3]",
                contributionAuth.getChainTaskId());
        e.printStackTrace();
        return false;
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
    public String getSconeSecureSession(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        SmsRequest smsRequest = buildSmsRequest(contributionAuth);

        ResponseEntity<String> sessionIdResponse = smsClient.generateTeeSession(smsRequest);

        if (sessionIdResponse.getBody() == null) {
            log.error("Received null session from SMS [chainTaskId:{}]", chainTaskId);
            return "";
        }

        return sessionIdResponse.getBody();
    }

    @Recover
    private Optional<SconeSecureSession> getSconeSecureSession(FeignException e, ContributionAuthorization contributionAuth) {
        log.error("Failed to generate secure session [chainTaskId:{}, attempts:3]",
                contributionAuth.getChainTaskId());
        e.printStackTrace();
        return Optional.empty();
    }

    public SmsRequest buildSmsRequest(ContributionAuthorization contributionAuth) {
        String hash = HashUtils.concatenateAndHash(contributionAuth.getWorkerWallet(),
                contributionAuth.getChainTaskId(), contributionAuth.getEnclaveChallenge());

        Sign.SignatureData workerSignature = Sign.signPrefixedMessage(
                BytesUtils.stringToBytes(hash), credentialsService.getCredentials().getEcKeyPair());

        SmsRequestData smsRequestData = SmsRequestData.builder()
            .chainTaskId(contributionAuth.getChainTaskId())
            .workerAddress(contributionAuth.getWorkerWallet())
            .enclaveChallenge(contributionAuth.getEnclaveChallenge())
            .coreSignature(contributionAuth.getSignature().getValue())
            .workerSignature(new Signature(workerSignature).getValue())
            .build();

        return new SmsRequest(smsRequestData);
    }
}