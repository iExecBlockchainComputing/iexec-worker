package com.iexec.worker.feign;

import java.util.List;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveredReplicateModel;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(
    name = "TaskClient",
    url = "http://${core.host}:${core.port}"
)
public interface TaskClient {

    @GetMapping("/tasks/interrupted")
    List<InterruptedReplicateModel> getInterruptedReplicates(
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @PostMapping("/tasks/recovered")
    void notifyOfRecovery(
            @RequestBody List<RecoveredReplicateModel> recoveredReplicates,
            // @RequestBody String recoveredReplicates,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @GetMapping("/tasks/available")
    ContributionAuthorization getAvailableReplicate(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;
}