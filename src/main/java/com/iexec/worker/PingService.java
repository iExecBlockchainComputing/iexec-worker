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
import com.iexec.worker.utils.AsyncUtils;
import com.iexec.worker.utils.ExecutorUtils;
import com.iexec.worker.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class PingService {

    private static final int PING_RATE = 10000; // 10s

    private final Executor executor;
    private final CustomCoreFeignClient customCoreFeignClient;
    private final CoreConfigurationService coreConfigurationService;
    private final WorkerService workerService;

    public PingService(CustomCoreFeignClient customCoreFeignClient,
                       CoreConfigurationService coreConfigurationService,
                       WorkerService workerService) {
        executor = ExecutorUtils
                .newSingleThreadExecutorWithFixedSizeQueue(1, "ping-");
        this.customCoreFeignClient = customCoreFeignClient;
        this.coreConfigurationService = coreConfigurationService;
        this.workerService = workerService;
    }

    /**
     * Trigger the scheduler ping every t seconds. The method that pings the
     * scheduler runs asynchronously inside a new thread to liberate the thread
     * used for @Scheduled tasks.
     * We use single thread executor to make sure the worker does not ping more
     * than once at the same time. The executors queue is of size 1 to avoid memory
     * leak if the thread halts for any reason.
     */
    @Scheduled(fixedRate = PING_RATE)
    void triggerSchedulerPing() {
        log.debug("Triggering scheduler ping action");
        AsyncUtils.triggerAsyncTask("ping", () -> pingScheduler(), executor);
    }

    /**
     * Send ping message to the scheduler and save the session id when pinging for
     * the first time. If the session id changes, it means that the scheduler has
     * restarted, os the worker needs to restart also.
     */
    void pingScheduler() {
        log.debug("Sending ping to scheduler");
        String sessionId = customCoreFeignClient.ping();
        // Log once in an hour, in the first ping of the first minute.
        if (LocalTime.now().getMinute() == 0
                && LocalTime.now().getSecond() <= PING_RATE) {
            log.info("Sent ping to scheduler [sessionId:{}]", sessionId);
        }
        if (StringUtils.isEmpty(sessionId)) {
            log.warn("The worker cannot ping the core! [sessionId:{}]", sessionId);
            return;
        }
        String currentSessionId = coreConfigurationService.getCoreSessionId();
        if (StringUtils.isEmpty(currentSessionId)) {
            log.info("First ping from the worker, setting the sessionId [coreSessionId:{}]", sessionId);
            coreConfigurationService.setCoreSessionId(sessionId);
            return;
        }
        if (!sessionId.equalsIgnoreCase(currentSessionId)) {
            // need to reconnect to the core by restarting the worker
            log.warn("Scheduler seems to have restarted [currentSessionId:{}, " +
                    "coreSessionId:{}]", currentSessionId, sessionId);
            workerService.restartGracefully();
        }
    }
}
