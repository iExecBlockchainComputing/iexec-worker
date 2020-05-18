package com.iexec.worker.replicate;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskManagerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;


@Slf4j
@Service
public class ReplicateDemandService {

    private final CustomCoreFeignClient customCoreFeignClient;
    private TaskManagerService taskManagerService;
    private IexecHubService iexecHubService;
    private SubscriptionService subscriptionService;
    private ContributionService contributionService;
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public ReplicateDemandService(IexecHubService iexecHubService,
                                  CustomCoreFeignClient customCoreFeignClient,
                                  SubscriptionService subscriptionService,
                                  ContributionService contributionService,
                                  ApplicationEventPublisher applicationEventPublisher,
                                  TaskManagerService taskManagerService) {
        this.customCoreFeignClient = customCoreFeignClient;
        this.iexecHubService = iexecHubService;
        this.subscriptionService = subscriptionService;
        this.contributionService = contributionService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.taskManagerService = taskManagerService;
    }

    @Scheduled(fixedRateString = "#{publicConfigurationService.askForReplicatePeriod}")
    public void askForReplicate() {
        // check if the worker can run a task or not
        if (!taskManagerService.canAcceptMoreReplicates()) {
            log.info("The worker is already full, it can't accept more tasks");
            return;
        }

        long lastAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        if (lastAvailableBlockNumber == 0) {
            log.error("Can't askForReplicate, your blockchain node seams unsync [lastAvailableBlockNumber:{}]",
                    lastAvailableBlockNumber);
            return;
        }

        if (!iexecHubService.hasEnoughGas()) {
            log.warn("The worker is out of gas, it cannot accept more tasks");
            return;
        }

        Optional<WorkerpoolAuthorization> oContributionAuth =
                customCoreFeignClient.getAvailableReplicate(lastAvailableBlockNumber);

        if (!oContributionAuth.isPresent()) {
            return;
        }

        WorkerpoolAuthorization workerpoolAuthorization = oContributionAuth.get();
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        log.info("Received new task [chainTaskId:{}]", chainTaskId);

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Task NOT initialized onchain [chainTaskId:{}]", chainTaskId);
            return;
        }

        TaskNotificationExtra notificationExtra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(workerpoolAuthorization)
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
