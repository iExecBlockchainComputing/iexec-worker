package com.iexec.worker;


import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.utils.WorkerConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    private CoreTaskClient coreTaskClient;

    private DockerService dockerService;
    private WorkerConfigurationService workerConfigurationService;

    @Autowired
    public Controller(CoreTaskClient coreTaskClient,
                      DockerService dockerService,
                      WorkerConfigurationService workerConfigurationService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerService = dockerService;
        this.workerConfigurationService = workerConfigurationService;
    }

    @GetMapping("/getTask")
    public String getTask() {
        String workerName = workerConfigurationService.getWorkerName();
        ReplicateModel replicate = coreTaskClient.getReplicate(workerName).getBody();
        if (replicate.getTaskId() == null) {
            return "NO TASK AVAILABLE";
        }

        coreTaskClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.RUNNING, workerName);

        // simulate some work on the task
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        coreTaskClient.updateReplicateStatus(replicate.getTaskId(), ReplicateStatus.COMPLETED, workerName);
        return ReplicateStatus.COMPLETED.toString();
    }

}