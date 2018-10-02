package com.iexec.worker.feign;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@FeignClient(name = "CoreClient", url = "${core.address}")
public interface CoreClient {

    @RequestMapping(method = RequestMethod.GET, path = "/version")
    String getCoreVersion();

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    PublicConfiguration getPublicConfiguration();

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping(@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    void registerWorker(@RequestBody WorkerConfigurationModel model);

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    ReplicateModel getReplicate(@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.POST, path = "/tasks/{taskId}/replicates/updateStatus")
    Optional<ReplicateModel> updateReplicateStatus(@PathVariable(name = "taskId") String taskId,
                                              @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                                              @RequestParam(name = "workerName") String workerName);
}