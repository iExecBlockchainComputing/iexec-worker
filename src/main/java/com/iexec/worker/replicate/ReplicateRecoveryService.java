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

package com.iexec.worker.replicate;

import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This service is used to remind the worker of possible interrupted works
 * after a restart and how to deal with each interruption.
 */
@Slf4j
@Service
public class ReplicateRecoveryService {

    private final CustomCoreFeignClient customCoreFeignClient;
    private final SubscriptionService subscriptionService;
    private final ResultService resultService;
    private final IexecHubService iexecHubService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReplicateRecoveryService(CustomCoreFeignClient customCoreFeignClient,
                                    SubscriptionService subscriptionService,
                                    ResultService resultService,
                                    IexecHubService iexecHubService,
                                    ApplicationEventPublisher applicationEventPublisher) {
        this.customCoreFeignClient = customCoreFeignClient;
        this.subscriptionService = subscriptionService;
        this.resultService = resultService;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public List<String> recoverInterruptedReplicates() {
        long latestAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        List<TaskNotification> missedTaskNotifications = customCoreFeignClient.getMissedTaskNotifications(
                latestAvailableBlockNumber);

        if (missedTaskNotifications == null || missedTaskNotifications.isEmpty()) {
            log.info("No interrupted tasks to recover");
            return Collections.emptyList();
        }

        return missedTaskNotifications.stream()
                .filter(this::canReplicateBeRecovered)
                .map(TaskNotification::getChainTaskId)
                .collect(Collectors.toList());
    }

    boolean canReplicateBeRecovered(TaskNotification missedTaskNotification) {
        final TaskNotificationType taskNotificationType = missedTaskNotification.getTaskNotificationType();
        final String chainTaskId = missedTaskNotification.getChainTaskId();
        final boolean isResultAvailable = resultService.isResultAvailable(chainTaskId);

        log.info("Recovering interrupted task [chainTaskId:{}, taskNotificationType:{}]",
                chainTaskId, taskNotificationType);

        if (!isResultAvailable) {
            log.error("Could not recover task, result not found [chainTaskId:{}, taskNotificationType:{}]",
                    chainTaskId, taskNotificationType);
            return false;
        }

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        if (taskDescription == null) {
            log.error("Could not recover task, no TaskDescription retrieved [chainTaskId:{}, taskNotificationType:{}]",
                    chainTaskId, taskNotificationType);
            return false;
        }

        final ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        resultService.saveResultInfo(chainTaskId, taskDescription, computedFile);
        subscriptionService.subscribeToTopic(chainTaskId);
        applicationEventPublisher.publishEvent(missedTaskNotification);

        return true;
    }
}
