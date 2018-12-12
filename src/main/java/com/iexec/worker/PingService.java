package com.iexec.worker;

import com.iexec.worker.feign.CustomFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private CustomFeignClient feignClient;

    public PingService(CustomFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        log.debug("Send ping to scheduler");
        feignClient.ping();
    }


}
