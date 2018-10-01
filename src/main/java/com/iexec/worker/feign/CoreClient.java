package com.iexec.worker.feign;

import com.iexec.common.replicate.Replicate;
import com.iexec.common.replicate.ReplicateStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@FeignClient(name = "CoreClient", url = "${core.address}")
public interface CoreClient {

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping(@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    Replicate getReplicate(@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.POST, path = "/tasks/{taskId}/replicates/updateStatus")
    Optional<Replicate> updateReplicateStatus(@PathVariable(name = "taskId") String taskId,
                                              @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                                              @RequestParam(name = "workerName") String workerName);
}