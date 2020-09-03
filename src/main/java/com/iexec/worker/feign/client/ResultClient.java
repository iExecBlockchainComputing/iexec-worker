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

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import feign.FeignException;


@FeignClient(name = "ResultRepoClient", url = "#{publicConfigurationService.resultRepositoryURL}")
public interface ResultClient {

    @GetMapping("/results/challenge")
    ResponseEntity<Eip712Challenge> getChallenge(@RequestParam(name = "chainId") Integer chainId) throws FeignException;

    @GetMapping("/results/login")
    ResponseEntity<String> login(@RequestParam(name = "chainId") Integer chainId,
                                          @RequestBody String signedEip712Challenge) throws FeignException;

    @PostMapping("/")
    ResponseEntity<String> uploadResult(@RequestHeader("Authorization") String authorizationToken,
                                        @RequestBody ResultModel resultModel);

    @GetMapping("/results/{chainTaskId}/ipfshash")
    ResponseEntity<String> getIpfsHashForTask(@PathVariable("chainTaskId") String chainTaskId);

}