package com.iexec.worker.replicate;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.CoreWorkerClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.utils.ContributionValidator;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class ReplicateDemandService {

    private CoreTaskClient coreTaskClient;
    private WorkerConfigurationService workerConfigService;
    private TaskExecutorService executorService;
    private SubscriptionService subscriptionService;

    private String corePublicAddress;

    @Autowired
    public ReplicateDemandService(CoreTaskClient coreTaskClient,
                                  WorkerConfigurationService workerConfigService,
                                  TaskExecutorService executorService,
                                  SubscriptionService subscriptionService,
                                  CoreWorkerClient coreWorkerClient) {
        this.coreTaskClient = coreTaskClient;
        this.workerConfigService = workerConfigService;
        this.executorService = executorService;
        this.subscriptionService = subscriptionService;

        corePublicAddress = coreWorkerClient.getPublicConfiguration().getSchedulerPublicAddress();
    }

    @Scheduled(fixedRate = 1000)
    public String askForReplicate() {
        // choose if the worker can run a task or not
        if (executorService.canAcceptMoreReplicate()) {

            AvailableReplicateModel model = coreTaskClient.getAvailableReplicate(
                    workerConfigService.getWorkerWalletAddress(),
                    workerConfigService.getWorkerEnclaveAdress());

            if (model == null) {
                return "NO TASK AVAILABLE";
            }

            String chainTaskId = model.getContributionAuthorization().getChainTaskId();
            log.info("Received task [chainTaskId:{}]", chainTaskId);

            // verify that the signature is valid
            ContributionAuthorization contribAuth = model.getContributionAuthorization();
            if( !ContributionValidator.isValid(contribAuth, corePublicAddress)){
                log.warn("The contribution authorization is NOT valid, the task will not be performed [chainTaskId:{}, contribAuth:{}]",
                        chainTaskId, contribAuth);
                return "Bad signature in received replicate";
            } else {
                log.info("The contribution authorization is valid [chainTaskId:{}]", chainTaskId);
                subscriptionService.subscribeToTaskNotifications(chainTaskId);
                executorService.addReplicate(model);
                return "Asked";
            }
        }
        log.info("The worker is already full, it can't accept more tasks");
        return "Worker cannot accept more task";
    }
}
