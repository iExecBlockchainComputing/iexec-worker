package com.iexec.worker;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "CoreClient", url = "${core.address}")
public interface CoreClient {

    @RequestMapping(method = RequestMethod.GET, path = "/hello")
    String hello(@RequestParam(name="name") String name);

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping(@RequestParam(name="workerName") String workerName);
}