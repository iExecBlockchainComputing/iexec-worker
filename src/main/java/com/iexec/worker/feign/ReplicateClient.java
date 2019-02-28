package com.iexec.worker.feign;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.ReplicateStatus;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "ReplicateClient", url = "http://${core.host}:${core.port}")
public interface ReplicateClient {

    @GetMapping("/replicates/available")
    ContributionAuthorization getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                    @RequestHeader("Authorization") String bearerToken) throws FeignException;

    // @GetMapping("/replicates/unfinished")
    // List<Replicate> getUnfinishedReplicate(String token);

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    void updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                               @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                               @RequestHeader("Authorization") String bearerToken,
                               @RequestBody ChainReceipt chainReceipt) throws FeignException;
}