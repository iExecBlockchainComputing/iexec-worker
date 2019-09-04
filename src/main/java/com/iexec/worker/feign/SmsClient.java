package com.iexec.worker.feign;


import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "SmsClient",
        url = "#{publicConfigurationService.smsURL}",
        configuration = FeignConfiguration.class)
public interface SmsClient {

    @PostMapping("/untee/secrets")
    SmsSecretResponse getUnTeeSecrets(@RequestBody SmsRequest smsRequest) throws FeignException;

    @PostMapping("/tee/sessions")
    ResponseEntity<String> generateTeeSession(@RequestBody SmsRequest smsRequest) throws FeignException;
}