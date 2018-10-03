package com.iexec.worker.feign;


import com.iexec.common.result.ResultModel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ResultRepoClient", url = "${core.address}")
public interface ResultRepoClient {

    @PostMapping("/results")
    ResponseEntity addResult(@RequestBody ResultModel resultModel);

}