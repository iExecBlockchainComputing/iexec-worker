/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.feign;

import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.replicate.ReplicateTaskSummary;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CustomCoreFeignClient extends BaseFeignClient {

    private final LoginService loginService;
    private final CoreClient coreClient;

    public CustomCoreFeignClient(CoreClient coreClient, LoginService loginService) {
        this.loginService = loginService;
        this.coreClient = coreClient;
    }

    /**
     * Log in the Scheduler.
     * Caution: this is NOT thread-safe.
     */
    @Override
    String login() {
        return loginService.login();
    }

    /*
     * How does it work?
     * We create an HttpCall<T>, T being the type of the response
     * body which can be Void. We send it along with the arguments
     * to the generic "makeHttpCall()" method. If the call was
     * successful, we return a ResponseEntity<T> with the response
     * body, otherwise, we return a ResponseEntity with the call's failure
     * status.
     *
     * How to pass call args?
     * We put call params in a Map<String, Object> (see below)
     * and we pass the Map as an argument to the lambda expression.
     * Inside the lambda expression we cast the arguments into their
     * original types required by the method to be called.
     * (Casting arguments is safe).
     */

    public String getCoreVersion() {
        return makeHttpCall(
                jwtToken -> coreClient.getCoreVersion(),
                "getCoreVersion", null, null);
    }

    public PublicConfiguration getPublicConfiguration() {
        return makeHttpCall(
                jwtToken -> coreClient.getPublicConfiguration(),
                "getPublicConfiguration", null, null);
    }

    //TODO: Make registerWorker return Worker
    public void registerWorker(WorkerModel model) {
        makeHttpCall(
                jwtToken -> coreClient.registerWorker(jwtToken, model),
                "registerWorker", loginService.getToken(), null);
    }

    public List<String> getComputingTasks() {
        return makeHttpCall(
                coreClient::getComputingTasks,
                "getComputingTasks", loginService.getToken(), Collections.emptyList());
    }

    public List<TaskNotification> getMissedTaskNotifications(long lastAvailableBlockNumber) {
        return makeHttpCall(
                jwtToken -> coreClient.getMissedTaskNotifications(jwtToken, lastAvailableBlockNumber),
                "getMissedNotifications", loginService.getToken(), Collections.emptyList());
    }

    public Optional<ReplicateTaskSummary> getAvailableReplicateTaskSummary(long lastAvailableBlockNumber) {
        try {
            return Optional.ofNullable(coreClient.getAvailableReplicateTaskSummary(
                    loginService.getToken(),
                    lastAvailableBlockNumber
            ));
        } catch (FeignException e) {
            log.error("Failed to retrieve work from scheduler [httpStatus:{}]", e.status());
            if (e instanceof FeignException.Unauthorized) {
                login();
            }
        }
        return Optional.empty();
    }

    public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatusUpdate replicateStatusUpdate) {
        try {
            final TaskNotificationType taskNotificationType = coreClient.updateReplicateStatus(
                    loginService.getToken(),
                    chainTaskId,
                    replicateStatusUpdate
            );
            log.info("Updated replicate status [status:{}, chainTaskId:{}]",
                    replicateStatusUpdate.getStatus(), chainTaskId);
            return taskNotificationType;
        } catch (FeignException e) {
            log.error("Exception while trying to update replicate status" +
                            " [chainTaskId:{}, statusUpdate:{}, httpStatus:{}]",
                    chainTaskId, replicateStatusUpdate, e.status());
            if (e instanceof FeignException.Unauthorized) {
                login();
            }
            return null;
        }
    }
}
