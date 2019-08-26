package com.iexec.worker.feign.client;


import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.scone.SconeSecureSessionResponse;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.config.FeignConfiguration;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "SmsClient",
        url = "#{publicConfigurationService.smsURL}",
        configuration = FeignConfiguration.class)
public interface SmsClient {

    @PostMapping("/secure")
    ResponseEntity<SmsSecretResponse> getTaskSecretsFromSms(@RequestBody SmsRequest smsRequest) throws FeignException;

    @PostMapping("/secure")
    ResponseEntity<SconeSecureSessionResponse> generateSecureSession(@RequestBody SmsRequest smsRequest) throws FeignException;
}