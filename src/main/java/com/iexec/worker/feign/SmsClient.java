package com.iexec.worker.feign;

import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.common.sms.tee.SmsSecureSessionResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import feign.FeignException;


@FeignClient(
    name = "SmsClient",
    url = "#{publicConfigurationService.smsURL}"
)
public interface SmsClient {

    @PostMapping("/secure")
    SmsSecretResponse getTaskSecrets(@RequestBody SmsRequest smsRequest) throws FeignException;

    @PostMapping("/securesession/generate")
    SmsSecureSessionResponse generateSecureSession(@RequestBody SmsRequest smsRequest) throws FeignException;
}