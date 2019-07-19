package com.iexec.worker.replicate;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskManagerService;
import com.iexec.worker.feign.CustomFeignClient;
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

    private final CustomFeignClient customFeignClient;
    private TaskManagerService taskManagerService;
    private IexecHubService iexecHubService;
    private SubscriptionService subscriptionService;
    private ContributionService contributionService;
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public ReplicateDemandService(IexecHubService iexecHubService,
                                  CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ContributionService contributionService,
                                  ApplicationEventPublisher applicationEventPublisher,
                                  TaskManagerService taskManagerService) {
        this.customFeignClient = customFeignClient;
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

        Optional<ContributionAuthorization> oContributionAuth =
                customFeignClient.getAvailableReplicate(lastAvailableBlockNumber);

        if (!oContributionAuth.isPresent()) {
            return;
        }

        ContributionAuthorization contributionAuth = oContributionAuth.get();

        String chainTaskId = contributionAuth.getChainTaskId();

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("task NOT initialized onchain [chainTaskId:{}]", chainTaskId);
            return;
        }

        TaskNotificationExtra notificationExtra = TaskNotificationExtra.builder()
                .contributionAuthorization(contributionAuth)
                .build();

        TaskNotification taskNotification = TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .workersAddress(Collections.emptyList())
                .taskNotificationType(TaskNotificationType.PLEASE_START)
                .taskNotificationExtra(notificationExtra)
                .build();

        //subscriptionService.handleSubscription(taskNotification);
        //taskNotificationService.onTaskNotification(taskNotification);

        subscriptionService.subscribeToTopic(chainTaskId);
        applicationEventPublisher.publishEvent(taskNotification);

    }
}
