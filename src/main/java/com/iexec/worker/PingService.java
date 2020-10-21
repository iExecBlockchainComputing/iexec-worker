/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker;

import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.worker.WorkerService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PingService {

    private final CustomCoreFeignClient customCoreFeignClient;
    private final CoreConfigurationService coreConfigurationService;
    private final WorkerService workerService;

    public PingService(CustomCoreFeignClient customCoreFeignClient,
                       CoreConfigurationService coreConfigurationService,
                       WorkerService workerService) {
        this.customCoreFeignClient = customCoreFeignClient;
        this.coreConfigurationService = coreConfigurationService;
        this.workerService = workerService;
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
            workerService.restartApp();
        }
    }
}
