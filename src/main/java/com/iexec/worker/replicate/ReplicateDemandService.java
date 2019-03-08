package com.iexec.worker.replicate;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
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

    @Autowired
    public ReplicateDemandService(TaskExecutorService taskExecutorService,
                                  IexecHubService iexecHubService,
                                  CustomFeignClient customFeignClient) {
        this.customFeignClient = customFeignClient;
        this.taskExecutorService = taskExecutorService;
        this.iexecHubService = iexecHubService;
    }

    @Scheduled(fixedRateString = "#{publicConfigurationService.askForReplicatePeriod}")
    public void askForReplicate() {
        // check if the worker can run a task or not
        long lastAvailableBlockNumber = iexecHubService.getLastBlock();
        if (!taskExecutorService.canAcceptMoreReplicates() && lastAvailableBlockNumber == 0) {
            log.info("The worker is already full, it can't accept more tasks");
            return;
        }

        ContributionAuthorization contribAuth = customFeignClient.getAvailableReplicate(
                lastAvailableBlockNumber);

        taskExecutorService.addReplicate(contribAuth);
    }
}
