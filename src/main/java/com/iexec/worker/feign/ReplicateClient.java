package com.iexec.worker.feign;

import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "ReplicateClient", url = "http://${core.host}:${core.port}")
public interface ReplicateClient {

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    void updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                               @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                               @RequestHeader("Authorization") String bearerToken,
                               @RequestBody ReplicateDetails details) throws FeignException;
}