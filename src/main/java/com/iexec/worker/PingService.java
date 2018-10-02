package com.iexec.worker;

import com.iexec.worker.feign.CoreClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private CoreClient coreClient;
    private WorkerConfigurationService workerConfService;

    public PingService(CoreClient coreClient,
                       WorkerConfigurationService workerConfService) {
        this.coreClient = coreClient;
        this.workerConfService = workerConfService;
    }

    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        log.info("try to ping scheduler");
        coreClient.ping(workerConfService.getWorkerName());
    }
}
