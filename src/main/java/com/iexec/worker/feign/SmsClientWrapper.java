package com.iexec.worker.feign;

import com.iexec.common.sms.SmsSecretRequest;
import com.iexec.common.sms.SmsSecretResponse;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class SmsClientWrapper {

    private SmsClient smsClient;

    public SmsClientWrapper(SmsClient smsClient) {
        this.smsClient = smsClient;
    }

    @Retryable(value = FeignException.class)
    public Optional<SmsSecretResponse> getTaskSecrets(SmsSecretRequest smsSecretRequest) {
        SmsSecretResponse smsSecretResponse = smsClient.getTaskSecrets(smsSecretRequest);

        if (!smsSecretResponse.isOk() || smsSecretResponse.getData() == null) {
            log.error("An error occured while getting task secrets [chainTaskId:{}, erroMsg:{}]",
                    smsSecretRequest.getChainTaskId(), smsSecretResponse.getErrorMessage());
            return Optional.empty();
        }

        return Optional.of(smsSecretResponse);
    }

    @Recover
    private Optional<SmsSecretResponse> getTaskSecrets(FeignException e, SmsSecretRequest smsSecretRequest) {
        if (e.status() == 404) {
            // no secret means we do not need to decrypt data
            return Optional.of(new SmsSecretResponse());
        }

        log.error("Failed to get task secrets from SMS [chainTaskId:{}, attempts:3]", smsSecretRequest.getChainTaskId());
        e.printStackTrace();
        return Optional.empty();
    }
}