package com.iexec.worker.feign;


import com.iexec.common.config.WorkerConfigurationModel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "CoreWorkerClient", url = "${core.address}")
public interface CoreWorkerClient {

    @RequestMapping(method = RequestMethod.GET, path = "/version")
    String getCoreVersion();

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    ResponseEntity getPublicConfiguration();

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping (@RequestParam(name = "workerName") String workerName);

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    void registerWorker(@RequestBody WorkerConfigurationModel model);
}