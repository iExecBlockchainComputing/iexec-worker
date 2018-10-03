package com.iexec.worker;

import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.CoreWorkerClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private final CoreWorkerClient coreWorkerClient;
    private WorkerConfigurationService workerConfService;

    public PingService(CoreWorkerClient coreWorkerClient,
                       WorkerConfigurationService workerConfService) {
        this.coreWorkerClient = coreWorkerClient;
        this.workerConfService = workerConfService;
    }


    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        log.info("try to ping scheduler");
        coreWorkerClient.ping(workerConfService.getWorkerName());
    }
}
