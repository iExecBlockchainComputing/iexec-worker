package com.iexec.worker.feign;

import com.iexec.common.sms.SmsSecretRequest;
import com.iexec.common.sms.SmsSecretResponse;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "SmsClient",
        url = "#{publicConfigurationService.smsURL}",
        configuration = FeignConfiguration.class)
public interface SmsClient {

    @PostMapping("/secure")
    SmsSecretResponse getTaskSecrets(@RequestBody SmsSecretRequest smsSecretRequest) throws FeignException;
}