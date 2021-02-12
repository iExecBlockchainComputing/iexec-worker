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

package com.iexec.worker.replicate;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;


@Slf4j
@Service
public class ReplicateDemandService {

    private final IexecHubService iexecHubService;
    private final CustomCoreFeignClient coreFeignClient;
    private final ContributionService contributionService;
    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReplicateDemandService(IexecHubService iexecHubService,
                                  CustomCoreFeignClient coreFeignClient,
                                  ContributionService contributionService,
                                  SubscriptionService subscriptionService,
                                  ApplicationEventPublisher applicationEventPublisher) {
        this.iexecHubService = iexecHubService;
        this.coreFeignClient = coreFeignClient;
        this.contributionService = contributionService;
        this.subscriptionService = subscriptionService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Asks for a new task every t seconds (e.g: t=30s)
     * then if received one locally starts computing the task
     */
    @Scheduled(fixedRateString = "#{publicConfigurationService.askForReplicatePeriod}")
    public void askForReplicate() {
        long lastAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        if (lastAvailableBlockNumber == 0) {
            log.error("Cannot ask for new tasks, your blockchain node is not synchronized");
            return;
        }
        if (!iexecHubService.hasEnoughGas()) {
            log.error("Cannot ask for new tasks, your wallet is dry");
            return;
        }
        coreFeignClient.getAvailableReplicate(lastAvailableBlockNumber)
                .filter(this::isNewTaskInitialized)
                .ifPresent(this::startTask);
    }

    /**
     * Checks if task is initialized
     *
     * @param authorization required authorization for later contribution
     * @return true if task is initialized
     */
    private boolean isNewTaskInitialized(WorkerpoolAuthorization authorization) {
        String chainTaskId = authorization.getChainTaskId();
        if (contributionService.isChainTaskInitialized(chainTaskId)) {
            log.info("Received new task [chainTaskId:{}]", chainTaskId);
            return true;
        }
        log.error("Received uninitialized task [chainTaskId:{}]", chainTaskId);
        return false;
    }

    /**
     * Starts task
     *
     * @param authorization required authorization for later contribution
     */
    private void startTask(WorkerpoolAuthorization authorization) {
        String chainTaskId = authorization.getChainTaskId();
        TaskNotificationExtra notificationExtra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(authorization)
                .build();
        TaskNotification taskNotification = TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .workersAddress(Collections.emptyList())
                .taskNotificationType(TaskNotificationType.PLEASE_START)
                .taskNotificationExtra(notificationExtra)
                .build();
        subscriptionService.subscribeToTopic(chainTaskId);
        applicationEventPublisher.publishEvent(taskNotification);
    }

}