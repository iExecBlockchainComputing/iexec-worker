package com.iexec.worker;

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private CustomCoreFeignClient customCoreFeignClient;
    private CoreConfigurationService coreConfigurationService;
    private RestartService restartService;

    public PingService(CustomCoreFeignClient customCoreFeignClient,
                       CoreConfigurationService coreConfigurationService,
                       RestartService restartService) {
        this.customCoreFeignClient = customCoreFeignClient;
        this.coreConfigurationService = coreConfigurationService;
        this.restartService = restartService;
    }

    @Scheduled(fixedRate = 10000)
    public void pingScheduler() {
        log.debug("Send ping to scheduler");
        String sessionId = customCoreFeignClient.ping();
        String currentSessionId = coreConfigurationService.getCoreSessionId();
        if (currentSessionId == null || currentSessionId.isEmpty()){
            log.info("First ping from the worker, setting the sessionId [coreSessionId:{}]", sessionId);
            coreConfigurationService.setCoreSessionId(sessionId);
            return;
        }

        if(sessionId == null || sessionId.isEmpty()) {
            log.warn("The worker cannot ping the core! [sessionId:{}]", sessionId);
            return;
        }

        if (!sessionId.equalsIgnoreCase(currentSessionId)) {
            // need to reconnect to the core by restarting the worker
            log.warn("Scheduler seems to have restarted [currentSessionId:{}, coreSessionId:{}]",
                    currentSessionId, sessionId);
            log.warn("The worker will restart now!");
            restartService.restartApp();
        }
    }
}
