package com.iexec.worker.feign;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "CoreWorkerClient", url = "http://${core.host}:${core.port}")
public interface CoreWorkerClient {

    @RequestMapping(method = RequestMethod.GET, path = "/version")
    String getCoreVersion();

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    PublicConfiguration getPublicConfiguration();

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping (@RequestParam(name = "walletAddress") String walletAddress);

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    void registerWorker(@RequestBody WorkerConfigurationModel model);
}