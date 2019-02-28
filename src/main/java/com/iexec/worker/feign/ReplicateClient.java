package com.iexec.worker.feign;

import com.iexec.common.replicate.ReplicateDetails;
import java.util.List;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "ReplicateClient", url = "https://${core.host}:${core.port}")
public interface ReplicateClient {

    @GetMapping("/replicates/available")
    ContributionAuthorization getAvailableReplicate(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @GetMapping("/replicates/interrupted")
    List<InterruptedReplicateModel> getInterruptedReplicates(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken
    ) throws FeignException;

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    void updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                               @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                               @RequestHeader("Authorization") String bearerToken,
                               @RequestBody ReplicateDetails details) throws FeignException;
}