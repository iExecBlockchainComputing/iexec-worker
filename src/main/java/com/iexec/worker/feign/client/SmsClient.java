package com.iexec.worker.feign.client;


import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.config.FeignConfiguration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import feign.FeignException;

@FeignClient(name = "SmsClient",
        url = "#{publicConfigurationService.smsURL}",
        configuration = FeignConfiguration.class)
public interface SmsClient {

    @PostMapping("/untee/secrets")
    ResponseEntity<SmsSecretResponse> getUnTeeSecrets(@RequestHeader("Authorization") String authorization,
                                                      @RequestBody ContributionAuthorization contributionAuth) throws FeignException;

    @PostMapping("/tee/sessions")
    ResponseEntity<String> createTeeSession(@RequestHeader("Authorization") String authorization,
                                              @RequestBody ContributionAuthorization contributionAuth) throws FeignException;

}