package com.iexec.worker.task;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.ResultModel;
import com.iexec.worker.docker.ContainerResult;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.ResultRepoClient;
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
    private ResultRepoClient resultRepoClient;

    @Autowired
    public TaskService(CoreTaskClient coreTaskClient, DockerService dockerService,
                       WorkerConfigurationService workerConfigService, ResultRepoClient resultRepoClient) {
        this.coreTaskClient = coreTaskClient;
        this.dockerService = dockerService;
        this.workerConfigService = workerConfigService;
        this.resultRepoClient = resultRepoClient;
    }

    @Scheduled(fixedRate = 30000)
    public String getTask() {
        String workerName = workerConfigService.getWorkerName();
        ReplicateModel replicateModel = coreTaskClient.getReplicate(workerName);
        if (replicateModel == null || replicateModel.getTaskId() == null) {
            return "NO TASK AVAILABLE";
        }
        log.info("Getting task [taskId:{}]", replicateModel.getTaskId());

        log.info("Update replicate status to RUNNING [taskId:{}, workerName:{}]", replicateModel.getTaskId(), workerName);
        coreTaskClient.updateReplicateStatus(replicateModel.getTaskId(), workerName, ReplicateStatus.RUNNING);

        if (replicateModel.getDappType().equals(DappType.DOCKER)) {
            ContainerResult containerResult = dockerService.dockerRun(replicateModel.getDappName(), replicateModel.getCmd());
            ResultModel resultModel = ResultModel.builder()
                    .taskId(replicateModel.getTaskId())
                    .image(containerResult.getImage())
                    .cmd(containerResult.getCmd())
                    .stdout(containerResult.getStdout())
                    .payload(null).build();
            resultRepoClient.addResult(resultModel);

        } else {
            // simulate some work on the task
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        log.info("Update replicate status to COMPLETED[taskId:{}, workerName:{}]", replicateModel.getTaskId(), workerName);
        coreTaskClient.updateReplicateStatus(replicateModel.getTaskId(), workerName, ReplicateStatus.COMPLETED);

        return ReplicateStatus.COMPLETED.toString();
    }


}
