package com.iexec.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    @Value("${worker.name}")
    private String workerName;

    private CoreClient coreClient;

    public TaskService(CoreClient coreClient) {
        this.coreClient = coreClient;
    }

    @Scheduled(fixedRate = 30000)
    public String getTask() {
        Replicate replicate = coreClient.getReplicate(workerName);
        if (replicate.getTaskId() == null){
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
