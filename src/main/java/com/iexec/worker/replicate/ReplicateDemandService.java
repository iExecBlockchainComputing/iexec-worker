package com.iexec.worker.replicate;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class ReplicateDemandService {

    private final CustomFeignClient feignClient;
    private TaskExecutorService executorService;
    private SubscriptionService subscriptionService;
    private ContributionService contributionService;

    private String corePublicAddress;
    private IexecHubService iexecHubService;

    @Autowired
    public ReplicateDemandService(TaskExecutorService executorService,
                                  SubscriptionService subscriptionService,
                                  ContributionService contributionService,
                                  IexecHubService iexecHubService,
                                  CustomFeignClient feignClient) {
        this.feignClient = feignClient;
        this.executorService = executorService;
        this.subscriptionService = subscriptionService;
        this.contributionService = contributionService;
        this.iexecHubService = iexecHubService;

        corePublicAddress = feignClient.getPublicConfiguration().getSchedulerPublicAddress();
    }

    @Scheduled(fixedRateString = "#{publicConfigurationService.askForReplicatePeriod}")
    public void askForReplicate() {
        // check if the worker can run a task or not
        long lastAvailableBlockNumber = iexecHubService.getLastBlock();
        if (!executorService.canAcceptMoreReplicate() && lastAvailableBlockNumber == 0) {
            log.info("The worker is already full, it can't accept more tasks");
            return;
        }

        ContributionAuthorization contribAuth = feignClient.getAvailableReplicate(lastAvailableBlockNumber);

        if (contribAuth == null) {
            // No task available;
            return;
        }

        String chainTaskId = contribAuth.getChainTaskId();
        log.info("Received task [chainTaskId:{}]", chainTaskId);

        // verify that the ContributionAuthorization is valid
        if (!contributionService.isContributionAuthorizationValid(contribAuth, corePublicAddress)) {
            log.error("The contribution contribAuth is NOT valid, the task will not be performed"
                    + " [chainTaskId:{}, contribAuth:{}]", chainTaskId, contribAuth);
            return;
        }

        // log.info("The contribution authorization is valid [chainTaskId:{}]", chainTaskId);
        subscriptionService.subscribeToTopic(chainTaskId);

        Optional<AvailableReplicateModel> optionalModel = retrieveAvailableReplicateModelFromContribAuth(contribAuth);
        if (!optionalModel.isPresent()) {
            log.error("Failed to retrieveAvailableReplicateModelFromContribAuth [chainTaskId:{}]", chainTaskId);
            return;
        }

        executorService.addReplicate(optionalModel.get());
        return;
    }

    private Optional<AvailableReplicateModel> retrieveAvailableReplicateModelFromContribAuth(
    ContributionAuthorization contribAuth) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(contribAuth.getChainTaskId());
        if (!optionalChainTask.isPresent()) {
            log.info("Failed to retrieve AvailableReplicate, ChainTask error  [chainTaskId:{}]",
                    contribAuth.getChainTaskId());
            return Optional.empty();
        }
        ChainTask chainTask = optionalChainTask.get();

        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainDeal.isPresent()) {
            log.info("Failed to retrieve AvailableReplicate, ChainDeal error  [chainTaskId:{}]", contribAuth.getChainTaskId());
            return Optional.empty();
        }
        ChainDeal chainDeal = optionalChainDeal.get();

        return Optional.of(AvailableReplicateModel.builder()
                .contributionAuthorization(contribAuth)
                .appType(DappType.DOCKER)
                .appUri(BytesUtils.hexStringToAscii(chainDeal.getChainApp().getUri()))
                .cmd(chainDeal.getParams().get(chainTask.getIdx()))
                .maxExecutionTime(chainDeal.getChainCategory().getMaxExecutionTime())
                .isTrustedExecution(TeeUtils.isTrustedExecutionTag(chainDeal.getTag()))
                .datasetUri(chainDeal.getChainDataset() != null ? BytesUtils.hexStringToAscii(chainDeal.getChainDataset().getUri()) : "")
                .build());
    }

}
