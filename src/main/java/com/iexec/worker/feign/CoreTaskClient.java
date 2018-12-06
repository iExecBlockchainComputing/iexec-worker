package com.iexec.worker.feign;


import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "CoreTaskClient", url = "http://${core.host}:${core.port}")
public interface CoreTaskClient {

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    AvailableReplicateModel getAvailableReplicate(@RequestParam(name = "workerWalletAddress") String workerWalletAddress,
                                                  @RequestParam(name = "workerEnclaveAddress") String workerEnclaveAddress);

    // TODO: this class should be split in two

    @RequestMapping(method = RequestMethod.POST, path = "/replicates/{chainTaskId}/updateStatus")
    ReplicateModel updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                                         @RequestParam(name = "walletAddress") String walletAddress,
                                         @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                                         @RequestHeader("Authorization") String bearerToken);

}