package com.iexec.worker.feign;


import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.ReplicateStatus;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "CoreTaskClient", url = "http://${core.host}:${core.port}")
public interface CoreTaskClient {

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    ContributionAuthorization getAvailableReplicate(@RequestHeader("Authorization") String bearerToken,
                                                    @RequestParam(name = "workerEnclaveAddress") String workerEnclaveAddress) throws FeignException;

    @RequestMapping(method = RequestMethod.POST, path = "/replicates/{chainTaskId}/updateStatus")
    void updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                               @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                               @RequestParam(name = "blockNumber") long blockNumber,
                               @RequestHeader("Authorization") String bearerToken) throws FeignException;

}