package com.iexec.worker.replicate;

import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class ReplicateDemandService {

    private final CustomFeignClient customFeignClient;
    private TaskExecutorService taskExecutorService;
    private IexecHubService iexecHubService;
    private SubscriptionService subscriptionService;
    private ReplicateService replicateService;
    private ContributionService contributionService;

    @Autowired
    public ReplicateDemandService(TaskExecutorService taskExecutorService,
                                  IexecHubService iexecHubService,
                                  CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ReplicateService replicateService,
                                  ContributionService contributionService) {
        this.customFeignClient = customFeignClient;
        this.taskExecutorService = taskExecutorService;
        this.iexecHubService = iexecHubService;
        this.subscriptionService = subscriptionService;
        this.replicateService = replicateService;
        this.contributionService = contributionService;
    }

    @Scheduled(fixedRateString = "#{publicConfigurationService.askForReplicatePeriod}")
    public void askForReplicate() {
        // check if the worker can run a task or not
        long lastAvailableBlockNumber = iexecHubService.getLastBlockNumber();
        if (!taskExecutorService.canAcceptMoreReplicates() && lastAvailableBlockNumber == 0) {
            log.info("The worker is already full, it can't accept more tasks");
            return;
        }

        ContributionAuthorization contributionAuth = customFeignClient.getAvailableReplicate(
                lastAvailableBlockNumber);

        if (contributionAuth == null) {
            return;
        }
        String chainTaskId = contributionAuth.getChainTaskId();

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("task NOT initialized onchain [chainTaskId:{}]", chainTaskId);
            return;
        }

        Optional<AvailableReplicateModel> oReplicateModel =
                replicateService.contributionAuthToReplicate(contributionAuth);

        if (!oReplicateModel.isPresent()) return;

        subscriptionService.subscribeToTopic(chainTaskId);
        
        taskExecutorService.addReplicate(oReplicateModel.get());
    }
}
