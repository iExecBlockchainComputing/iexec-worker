package com.iexec.worker.replicate;

import com.iexec.common.chain.*;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.CoreWorkerClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;


@Slf4j
@Service
public class ReplicateDemandService {

    private CoreTaskClient coreTaskClient;
    private WorkerConfigurationService workerConfigService;
    private TaskExecutorService executorService;
    private SubscriptionService subscriptionService;
    private ContributionService contributionService;

    private String corePublicAddress;
    private IexecHubService iexecHubService;

    @Autowired
    public ReplicateDemandService(CoreTaskClient coreTaskClient,
                                  WorkerConfigurationService workerConfigService,
                                  TaskExecutorService executorService,
                                  SubscriptionService subscriptionService,
                                  CoreWorkerClient coreWorkerClient,
                                  ContributionService contributionService,
                                  IexecHubService iexecHubService) {
        this.coreTaskClient = coreTaskClient;
        this.workerConfigService = workerConfigService;
        this.executorService = executorService;
        this.subscriptionService = subscriptionService;
        this.contributionService = contributionService;

        corePublicAddress = coreWorkerClient.getPublicConfiguration().getSchedulerPublicAddress();
        this.iexecHubService = iexecHubService;
    }

    @Scheduled(fixedRate = 1000)
    public String askForReplicate() {
        // choose if the worker can run a task or not
        if (executorService.canAcceptMoreReplicate()) {

            ContributionAuthorization contribAuth = coreTaskClient.getAvailableReplicate(
                    workerConfigService.getWorkerWalletAddress(),
                    workerConfigService.getWorkerEnclaveAdress());

            if (contribAuth == null) {
                return "NO TASK AVAILABLE";
            }

            String chainTaskId = contribAuth.getChainTaskId();
            log.info("Received task [chainTaskId:{}]", chainTaskId);

            // verify that the signature is valid
            if( !contributionService.isContributionAuthorizationValid(contribAuth, corePublicAddress)){
                log.warn("The contribution contribAuth is NOT valid, the task will not be performed [chainTaskId:{}, contribAuth:{}]",
                        chainTaskId, contribAuth);
                return "Bad signature in received replicate";
            } else {
                log.info("The contribution contribAuth is valid [chainTaskId:{}]", chainTaskId);
                subscriptionService.subscribeToTaskNotifications(chainTaskId);

                Optional<AvailableReplicateModel> optionalModel = retrieveAvailableReplicateModelFromContribAuth(contribAuth);
                if (!optionalModel.isPresent()){
                    log.info("Failed to retrieveAvailableReplicateModelFromContribAuth [chainTaskId:{}]", chainTaskId);
                    return "Failed to retrieveAvailableReplicateModelFromContribAuth";
                }

                executorService.addReplicate(optionalModel.get());
                return "Asked";
            }
        }
        log.info("The worker is already full, it can't accept more tasks");
        return "Worker cannot accept more task";
    }

    private Optional<AvailableReplicateModel> retrieveAvailableReplicateModelFromContribAuth(ContributionAuthorization contribAuth) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(contribAuth.getChainTaskId());
        if (!optionalChainTask.isPresent()){
            log.info("Failed to retrieve AvailableReplicate, ChainTask error  [chainTaskId:{}]", contribAuth.getChainTaskId());
            return Optional.empty();
        }
        ChainTask chainTask = optionalChainTask.get();

        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainDeal.isPresent()){
            log.info("Failed to retrieve AvailableReplicate, ChainDeal error  [chainTaskId:{}]", contribAuth.getChainTaskId());
            return Optional.empty();
        }
        ChainDeal chainDeal = optionalChainDeal.get();

        return Optional.of(AvailableReplicateModel.builder()
                .contributionAuthorization(contribAuth)
                .dappType(DappType.DOCKER)
                .dappName(chainDeal.getChainApp().getParams().getUri())
                .cmd(chainDeal.getParams().get(chainTask.getIdx()))
                .maxExecutionTime(chainDeal.getChainCategory().getMaxExecutionTime())
                .build());
    }
}
