package com.iexec.worker;

import com.iexec.worker.feign.CoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private CoreClient coreClient;

    public PingService(CoreClient coreClient){
        this.coreClient = coreClient;
    }

    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        log.info("try to ping scheduler");
        coreClient.ping("customWorker");
    }
}
