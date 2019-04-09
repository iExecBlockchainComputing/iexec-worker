package com.iexec.worker.feign;

import com.iexec.common.sms.SmsSecretRequest;
import com.iexec.common.sms.SmsSecretResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import feign.FeignException;


@FeignClient(
    name = "SmsClient",
    url = "#{publicConfigurationService.smsURL}"
)
public interface SmsClient {

    @PostMapping("/secret")
    SmsSecretResponse getTaskSecrets(@RequestBody SmsSecretRequest smsSecretRequest) throws FeignException;
}