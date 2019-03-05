package com.iexec.worker.feign;

import java.util.List;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.RecoverableAction;
import com.iexec.common.replicate.InterruptedReplicatesModel;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(
    name = "TaskClient",
    url = "http://${core.host}:${core.port}"
)
public interface TaskClient {

    @GetMapping("/tasks/interrupted")
    InterruptedReplicatesModel getInterruptedReplicates(
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @PostMapping("/tasks/recovered")
    InterruptedReplicatesModel notifyOfRecovery(
            @RequestParam("interruptedAction") RecoverableAction interruptedAction,
            @RequestBody() List<String> chainTaskIdList,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @GetMapping("/tasks/available")
    ContributionAuthorization getAvailableReplicate(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;
}