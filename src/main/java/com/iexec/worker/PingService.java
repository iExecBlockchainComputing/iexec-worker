package com.iexec.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PingService {

    private CoreClient coreClient;

    public PingService(CoreClient coreClient){
        this.coreClient = coreClient;
    }

    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        System.out.println("try to ping scheduler");
        coreClient.ping("customWorker");
    }
}
