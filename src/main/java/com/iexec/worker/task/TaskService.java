package com.iexec.worker.task;

import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CoreTaskClient;
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

    @Autowired
    public TaskService(CoreTaskClient coreTaskClient,
                       WorkerConfigurationService workerConfigService,
                       TaskExecutorService executorService) {
        this.coreTaskClient = coreTaskClient;
        this.workerConfigService = workerConfigService;
        this.executorService = executorService;
    }

    @Scheduled(fixedRate = 1000)
    public String getTask() {
        // choose if the worker can run a task or not
        if (executorService.canAcceptMoreReplicate()) {
            String workerName = workerConfigService.getWorkerName();
            ReplicateModel replicateModel = coreTaskClient.getReplicate(workerName);
            if (replicateModel == null || replicateModel.getTaskId() == null) {

                return "NO TASK AVAILABLE";
            }
            log.info("Received task [taskId:{}]", replicateModel.getTaskId());

            executorService.addReplicate(replicateModel);
            return ReplicateStatus.COMPUTED.toString();
        }
        log.info("The worker is already full, it can't accept more tasks");
        return "Worker cannot accept more task";
    }
}
