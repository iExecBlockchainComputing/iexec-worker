package com.iexec.worker.feign;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.security.Signature;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "CoreWorkerClient", url = "http://${core.host}:${core.port}")
public interface CoreWorkerClient {

    @RequestMapping(method = RequestMethod.GET, path = "/version")
    String getCoreVersion();

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    PublicConfiguration getPublicConfiguration();

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    void ping(@RequestHeader("Authorization") String bearerToken) throws FeignException;

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    void registerWorker(@RequestHeader("Authorization") String bearerToken,
                        @RequestBody WorkerConfigurationModel model) throws FeignException;

    @RequestMapping(method = RequestMethod.POST, path = "/workers/login")
    String login(@RequestParam(name = "walletAddress") String walletAddress,
                 @RequestBody Signature authorization);

    @RequestMapping(method = RequestMethod.GET, path = "/workers/challenge")
    String getChallenge(@RequestParam(name = "walletAddress") String walletAddress);

}