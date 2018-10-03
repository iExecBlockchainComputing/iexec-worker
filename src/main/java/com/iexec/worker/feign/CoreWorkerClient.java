package com.iexec.worker.feign;


import com.iexec.common.core.WorkerInterface;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "CoreWorkerClient", url = "${core.address}")
public interface CoreWorkerClient extends WorkerInterface {


}