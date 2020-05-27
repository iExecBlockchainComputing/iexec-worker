package com.iexec.worker.feign.client;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import feign.FeignException;


@FeignClient(name = "ResultRepoClient", url = "#{publicConfigurationService.resultRepositoryURL}")
public interface ResultClient {

    @GetMapping("/results/challenge")
    ResponseEntity<Eip712Challenge> getChallenge(@RequestParam(name = "chainId") Integer chainId) throws FeignException;

    @GetMapping("/results/login")
    ResponseEntity<String> login(@RequestParam(name = "chainId") Integer chainId,
                                          @RequestBody String signedEip712Challenge) throws FeignException;

    @PostMapping("/results")
    ResponseEntity<String> uploadResult(@RequestHeader("Authorization") String authorizationToken,
                                        @RequestBody ResultModel resultModel);

}