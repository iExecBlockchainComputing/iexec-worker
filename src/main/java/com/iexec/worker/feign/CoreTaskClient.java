package com.iexec.worker.feign;


import com.iexec.common.core.TaskInterface;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "CoreTaskClient", url = "${core.address}")
public interface CoreTaskClient extends TaskInterface {


}