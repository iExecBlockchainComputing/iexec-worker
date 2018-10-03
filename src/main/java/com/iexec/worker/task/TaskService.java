package com.iexec.worker.task;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.docker.DockerService;
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
    private DockerService dockerService;
    private WorkerConfigurationService workerConfigService;

    @Autowired
    public TaskService(CoreTaskClient coreTaskClient, DockerService dockerService,  WorkerConfigurationService workerConfigService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerService = dockerService;
        this.workerConfigService = workerConfigService;
    }

    @Scheduled(fixedRate = 30000)
    public String getTask() {
        String workerName = workerConfigService.getWorkerName();
        ReplicateModel replicateModel = coreTaskClient.getReplicate(workerName).getBody();
        if (replicateModel == null || replicateModel.getTaskId() == null) {
            return "NO TASK AVAILABLE";
        }
        log.info("Getting task [taskId:{}]", replicateModel.getTaskId());


        log.info(replicateModel.getTaskId());
        coreTaskClient.updateReplicateStatus(replicateModel.getTaskId(), ReplicateStatus.RUNNING, workerName);

        if (replicateModel.getDappType().equals(DappType.DOCKER)) {
            dockerService.dockerRun(replicateModel.getDappName(), replicateModel.getCmd());
        } else {
            // simulate some work on the task
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        coreTaskClient.updateReplicateStatus(replicateModel.getTaskId(), ReplicateStatus.COMPLETED, workerName);

        return ReplicateStatus.COMPLETED.toString();
    }


}
