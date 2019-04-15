package com.iexec.worker.feign;

import com.iexec.common.sms.SmsSecretRequest;
import com.iexec.common.sms.SmsSecretRequestBody;
import com.iexec.common.sms.SmsSecretResponse;
import com.iexec.common.sms.TaskSecrets;

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
    public Optional<TaskSecrets> getTaskSecrets(SmsSecretRequestBody smsSecretRequestBody) {
        SmsSecretRequest smsSecretRequest = new SmsSecretRequest(smsSecretRequestBody);
        SmsSecretResponse smsSecretResponse = smsClient.getTaskSecrets(smsSecretRequest);

        if (!smsSecretResponse.isOk()) {
            log.error("An error occured while getting task secrets [chainTaskId:{}, erroMsg:{}]",
                    smsSecretRequestBody.getChainTaskId(), smsSecretResponse.getErrorMessage());
            return Optional.empty();
        }

        return Optional.of(smsSecretResponse.getData().getSecrets());
    }

    @Recover
    private Optional<TaskSecrets> getTaskSecrets(FeignException e, SmsSecretRequestBody smsSecretRequestBody) {
        log.error("Failed to get task secrets from SMS [chainTaskId:{}, attempts:3]",
                smsSecretRequestBody.getChainTaskId());
        e.printStackTrace();
        return Optional.empty();
    }
}