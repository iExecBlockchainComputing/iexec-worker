package com.iexec.worker.feign;

import com.iexec.common.chain.ContributionAuthorization;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(
    name = "TaskClient",
    url = "http://${core.host}:${core.port}"
)
public interface TaskClient {

    @GetMapping("/tasks/available")
    ContributionAuthorization getAvailableReplicate(
        @RequestParam(name = "blockNumber") long blockNumber,
        @RequestHeader("Authorization") String bearerToken) throws FeignException;
}