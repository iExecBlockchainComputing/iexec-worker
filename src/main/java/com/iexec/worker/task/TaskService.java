package com.iexec.worker.task;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskService {

    @Value("${worker.name}")
    private String workerName;

    private CoreClient coreClient;
    private DockerService dockerService;

    @Autowired
    public TaskService(CoreClient coreClient, DockerService dockerService) {
        this.coreClient = coreClient;
        this.dockerService = dockerService;
    }

    @Scheduled(fixedRate = 30000)
    public String getTask() {
        ReplicateModel replicateModel = coreClient.getReplicate(workerName);
        log.info("Getting task [taskId:{}]", replicateModel.getTaskId());
        if (replicateModel == null || replicateModel.getTaskId() == null){
            return "NO TASK AVAILABLE";
        }

        coreClient.updateReplicateStatus(replicateModel.getTaskId(), ReplicateStatus.RUNNING, workerName);


        if (replicateModel.getDappType().equals(DappType.DOCKER)){
            dockerService.dockerRun(replicateModel.getDappName(), replicateModel.getCmd());
        } else {
            // simulate some work on the task
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }



        coreClient.updateReplicateStatus(replicateModel.getTaskId(), ReplicateStatus.COMPLETED, workerName);
        return ReplicateStatus.COMPLETED.toString();
    }

}
