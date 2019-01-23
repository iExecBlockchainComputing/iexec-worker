package com.iexec.worker.feign;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import feign.FeignException;

@FeignClient(name = "ResultRepoClient", url = "http://${core.host}:${core.port}")
public interface ResultRepoClient {

    @RequestMapping(method=RequestMethod.GET, path="/results/challenge")
    Eip712Challenge getChallenge(@RequestParam(name = "chainId") Integer chainId) throws FeignException;

                             @PostMapping("/results")
    ResponseEntity uploadResult(@RequestHeader("Authorization") String customToken,
                             @RequestBody ResultModel resultModel);

}