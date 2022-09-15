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
import com.iexec.common.replicate.ReplicateDemandResponse;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.utils.AsyncUtils;
import com.iexec.worker.utils.ExecutorUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class ReplicateDemandService {

    private final Executor executor;
    private final IexecHubService iexecHubService;
    private final CustomCoreFeignClient coreFeignClient;
    private final ContributionService contributionService;
    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Lock askForReplicateLock = new ReentrantLock();

    public ReplicateDemandService(IexecHubService iexecHubService,
                                  CustomCoreFeignClient coreFeignClient,
                                  ContributionService contributionService,
                                  SubscriptionService subscriptionService,
                                  ApplicationEventPublisher applicationEventPublisher) {
        executor = ExecutorUtils
                .newSingleThreadExecutorWithFixedSizeQueue(1, "ask-for-rep-");
        this.iexecHubService = iexecHubService;
        this.coreFeignClient = coreFeignClient;
        this.contributionService = contributionService;
        this.subscriptionService = subscriptionService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Trigger ask for replicate action every t seconds (e.g: t=30s).
     * The method that asks for the replicate runs asynchronously inside a new
     * thread to liberate the thread used for @Scheduled tasks.
     * We use single thread executor to make sure the worker does not ask for more
     * than one replicate at the same time. The executor's queue is of size 1 to
     * avoid memory leak if the thread halts for any reason.
     */
    @Scheduled(fixedDelayString = "#{publicConfigurationService.askForReplicatePeriod}")
    void triggerAskForReplicate() {
        log.debug("Triggering ask for replicate action");
        AsyncUtils.runAsyncTask("ask-for-replicate", this::askForReplicate, executor);
    }

    /**
     * Ask for a new replicate. Check all conditions are satisfied before starting
     * to execute the task.
     */
    void askForReplicate() {
        if (!askForReplicateLock.tryLock()) {
            return;
        }

        try {
            log.debug("Asking for a new replicate");
            // TODO check blocknumber only once a replicate is received.
            long lastAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
            if (lastAvailableBlockNumber == 0) {
                log.error("Cannot ask for new tasks, your blockchain node is not synchronized");
                return;
            }
            // TODO check gas only once a replicate is received.
            if (!iexecHubService.hasEnoughGas()) {
                log.error("Cannot ask for new tasks, your wallet is dry");
                return;
            }
            coreFeignClient.getAvailableReplicate(lastAvailableBlockNumber)
                    .filter(this::isNewTaskInitialized)
                    .ifPresent(this::startTask);
        } finally {
            askForReplicateLock.unlock();
        }
    }

    /**
     * Checks if task is initialized
     *
     * @param authorization required authorization for later contribution
     * @return true if task is initialized
     */
    private boolean isNewTaskInitialized(ReplicateDemandResponse authorization) {
        String chainTaskId = authorization.getWorkerpoolAuthorization().getChainTaskId();
        log.info("Received new task [chainTaskId:{}]", chainTaskId);
        if (contributionService.isChainTaskInitialized(chainTaskId)) {
            log.info("Incoming task exists on-chain [chainTaskId:{}]", chainTaskId);
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
    void startTask(ReplicateDemandResponse replicateDemandResponse) {
        WorkerpoolAuthorization authorization = 
            replicateDemandResponse.getWorkerpoolAuthorization();
        String chainTaskId = authorization.getChainTaskId();
        TaskNotificationExtra notificationExtra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(authorization)
                .smsUrl(replicateDemandResponse.getSmsUrl())
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
