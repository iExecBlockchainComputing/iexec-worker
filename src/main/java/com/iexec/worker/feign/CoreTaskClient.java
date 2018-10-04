package com.iexec.worker.feign;


import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "CoreTaskClient", url = "${core.address}")
public interface CoreTaskClient {

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    ReplicateModel getReplicate(@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.POST, path = "/tasks/{taskId}/replicates/updateStatus")
    ReplicateModel updateReplicateStatus(@PathVariable(name = "taskId") String taskId,
                                         @RequestParam(name = "workerName") String workerName,
                                         @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus);

}