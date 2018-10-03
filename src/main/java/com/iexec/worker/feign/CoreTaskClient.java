package com.iexec.worker.feign;


import com.iexec.common.core.TaskInterface;
import com.iexec.common.core.WorkerInterface;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "CoreTaskClient", url = "${core.address}")
public interface CoreTaskClient extends TaskInterface {


}