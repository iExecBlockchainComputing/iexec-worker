package com.iexec.worker.feign;


import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "CoreTaskClient", url = "http://${core.host}:${core.port}")
public interface CoreTaskClient {

    @GetMapping("/tasks/available")
    ContributionAuthorization getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                    @RequestHeader("Authorization") String bearerToken) throws FeignException;

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    void updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                               @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                               @RequestHeader("Authorization") String bearerToken,
                               @RequestBody ReplicateDetails details) throws FeignException;
}