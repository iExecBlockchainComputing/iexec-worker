package com.iexec.worker.task;

import com.iexec.common.replicate.Replicate;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.feign.CoreClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskService {


    private CoreClient coreClient;
    private WorkerConfigurationService workerConfigService;

    public TaskService(CoreClient coreClient, WorkerConfigurationService workerConfigurationService) {
        this.coreClient = coreClient;
        this.workerConfigService = workerConfigurationService;
    }

    @Scheduled(fixedRate = 30000)
    public String getTask() {
        String workerName = workerConfigService.getWorkerName();
        Replicate replicate = coreClient.getReplicate(workerName);
        if (replicate == null || replicate.getTaskId() == null){
            return "NO TASK AVAILABLE";
        }

        coreClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.RUNNING, workerName);

        // simulate some work on the task
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        coreClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.COMPLETED, workerName);
        return ReplicateStatus.COMPLETED.toString();
    }

}
