package com.iexec.worker.sms;

import java.io.File;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.sms.secrets.SmsSecret;
import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.SmsRequestData;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.common.sms.secrets.TaskSecrets;
import com.iexec.common.sms.tee.SmsSecureSessionResponse;
import com.iexec.common.sms.tee.SmsSecureSessionResponse.SmsSecureSession;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.SmsClient;
import com.iexec.worker.utils.FileHelper;

import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SmsService {

    // secret file names
    private static final String DATASET_SECRET_FILENAME = "dataset.secret";
    private static final String BENEFICIARY_SECRET_FILENAME = "beneficiary.secret";
    private static final String ENCLAVE_SECRET_FILENAME = "enclave.secret";

    private SmsClient smsClient;
    private CredentialsService credentialsService;
    private WorkerConfigurationService workerConfigurationService;

    public SmsService(SmsClient smsClient,
                      CredentialsService credentialsService,
                      WorkerConfigurationService workerConfigurationService) {
        this.smsClient = smsClient;
        this.credentialsService = credentialsService;
        this.workerConfigurationService = workerConfigurationService;
    }

    @Retryable(value = FeignException.class)
    public boolean fetchTaskSecrets(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        SmsRequest smsRequest = buildSmsRequest(contributionAuth);

        SmsSecretResponse smsResponse = smsClient.getTaskSecrets(smsRequest);

        if (smsResponse == null) {
            log.error("Received null response from SMS [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (!smsResponse.isOk()) {
            log.error("An error occurred while getting task secrets [chainTaskId:{}, errorMessage:{}]",
                    chainTaskId, smsResponse.getErrorMessage());
            return false;
        }

        TaskSecrets taskSecrets = smsResponse.getData().getSecrets();

        if (taskSecrets == null) {
            log.error("Received null secrets object from SMS [chainTaskId:{}]", chainTaskId);
            return false;
        }

        saveSecrets(chainTaskId, taskSecrets);
        return true;
    }

    @Recover
    private boolean fetchTaskSecrets(FeignException e, ContributionAuthorization contributionAuth) {
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, attempts:3]",
                contributionAuth.getChainTaskId());
        e.printStackTrace();
        return false;
    }

    public void saveSecrets(String chainTaskId, TaskSecrets taskSecrets) {

        SmsSecret datasetSecret = taskSecrets.getDatasetSecret();
        SmsSecret beneficiarySecret = taskSecrets.getBeneficiarySecret();
        SmsSecret enclaveSecret = taskSecrets.getEnclaveSecret();

        if (datasetSecret != null && datasetSecret.getSecret() != null) {
            FileHelper.createFileWithContent(getDatasetSecretFilePath(chainTaskId), datasetSecret.getSecret() + "\n");
            log.info("Downloaded dataset secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No dataset secret found for this task [chainTaskId:{}]", chainTaskId);
        }

        if (beneficiarySecret != null && beneficiarySecret.getSecret() != null) {
            FileHelper.createFileWithContent(getBeneficiarySecretFilePath(chainTaskId), beneficiarySecret.getSecret());
            log.info("Downloaded beneficiary secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No beneficiary secret found for this task [chainTaskId:{}]", chainTaskId);
        }

        if (enclaveSecret != null && enclaveSecret.getSecret() != null) {
            FileHelper.createFileWithContent(getEnclaveSecretFilePath(chainTaskId), enclaveSecret.getSecret());
            log.info("Downloaded enclave secret [chainTaskId:{}]", chainTaskId);
        } else {
            log.info("No enclave secret found for this task [chainTaskId:{}]", chainTaskId);
        }
    }

    @Retryable(value = FeignException.class)
    public Optional<SmsSecureSession> generateTaskSecureSession(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();
        SmsRequest smsRequest = buildSmsRequest(contributionAuth);

        SmsSecureSessionResponse smsResponse = smsClient.generateSecureSession(smsRequest);

        if (smsResponse == null) {
            log.error("Received null response from SMS  [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        if (!smsResponse.isOk()) {
            log.error("An error occurred while generating secure session [chainTaskId:{}, errorMessage:{}]",
                    chainTaskId, smsResponse.getErrorMessage());
            return Optional.empty();
        }

        if (smsResponse.getData() == null) {
            log.error("Received null session from SMS [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        return Optional.of(smsResponse.getData());
    }

    @Recover
    private Optional<SmsSecureSession> generateTaskSecureSession(FeignException e, ContributionAuthorization contributionAuth) {
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

    public String getDatasetSecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/input/dataset.secret
        return workerConfigurationService.getTaskInputDir(chainTaskId)
                + File.separator + DATASET_SECRET_FILENAME;
    }

    public String getBeneficiarySecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/beneficiary.secret
        return workerConfigurationService.getTaskBaseDir(chainTaskId)
                + File.separator + BENEFICIARY_SECRET_FILENAME;
    }

    public String getEnclaveSecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/enclave.secret
        return workerConfigurationService.getTaskBaseDir(chainTaskId)
                + File.separator + ENCLAVE_SECRET_FILENAME;
    }
}