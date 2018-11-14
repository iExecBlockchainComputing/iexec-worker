package com.iexec.worker.task;

import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.utils.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class TaskService {

    private CoreTaskClient coreTaskClient;
    private WorkerConfigurationService workerConfigService;
    private TaskExecutorService executorService;
    private SubscriptionService subscriptionService;


    @Autowired
    public TaskService(CoreTaskClient coreTaskClient,
                       WorkerConfigurationService workerConfigService,
                       TaskExecutorService executorService, SubscriptionService subscriptionService) {
        this.coreTaskClient = coreTaskClient;
        this.workerConfigService = workerConfigService;
        this.executorService = executorService;
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(fixedRate = 1000)
    public String getTask() {
        // choose if the worker can run a task or not
        if (executorService.canAcceptMoreReplicate()) {

            AvailableReplicateModel replicateModel = coreTaskClient.getAvailableReplicate(
                    workerConfigService.getWorkerWalletAddress(),
                    workerConfigService.getWorkerEnclaveAdress());
            if (replicateModel == null || replicateModel.getTaskId() == null) {
                return "NO TASK AVAILABLE";
            }

            log.info("Received task [taskId:{}]", replicateModel.getTaskId());
            subscriptionService.subscribeToTaskNotifications(replicateModel.getTaskId());
            executorService.addReplicate(replicateModel);
            return ReplicateStatus.COMPUTED.toString();
        }
        log.info("The worker is already full, it can't accept more tasks");
        return "Worker cannot accept more task";
    }

}
