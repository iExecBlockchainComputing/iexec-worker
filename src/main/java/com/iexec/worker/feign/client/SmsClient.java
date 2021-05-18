/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.feign.client;


import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.precompute.PreComputeConfig;
import com.iexec.common.sms.secret.SmsSecretResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import feign.FeignException;

@FeignClient(name = "SmsClient", url = "#{publicConfigurationService.smsURL}", decode404 = true)
public interface SmsClient {

    @PostMapping("/untee/secrets")
    ResponseEntity<SmsSecretResponse> getUnTeeSecrets(
            @RequestHeader("Authorization") String authorization,
            @RequestBody WorkerpoolAuthorization workerpoolAuthorization) throws FeignException;

    @PostMapping("/tee/sessions")
    ResponseEntity<String> createTeeSession(
            @RequestHeader("Authorization") String authorization,
            @RequestBody WorkerpoolAuthorization workerpoolAuthorization) throws FeignException;

    @GetMapping("/precompute/config")
    ResponseEntity<PreComputeConfig> getPreComputeConfiguration() throws FeignException;

}