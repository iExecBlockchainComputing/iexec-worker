package com.iexec.worker.feign;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "CoreClient",
        url = "${core.protocol}://${core.host}:${core.port}",
        configuration = FeignConfiguration.class)
public interface CoreClient {

    @GetMapping("/version")
    String getCoreVersion() throws FeignException;
}