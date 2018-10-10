package com.iexec.worker.executor;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.docker.MetadataResult;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.ResultRepoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class TaskExecutorService {

    private CoreTaskClient coreTaskClient;
    private DockerService dockerService;
    private ResultRepoClient resultRepoClient;
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(CoreTaskClient coreTaskClient,
                               DockerService dockerService,
                               ResultRepoClient resultRepoClient) {
        this.coreTaskClient = coreTaskClient;
        this.dockerService = dockerService;
        this.resultRepoClient = resultRepoClient;
        maxNbExecutions = Runtime.getRuntime().availableProcessors() / 2;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicate() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public void addReplicate(ReplicateModel model) {
        String taskId = model.getTaskId();
        String workerName = model.getWorkerAddress();

        CompletableFuture.supplyAsync(() -> {

                    // TODO: this part should be refactored
                    log.info("Update replicate status to RUNNING [taskId:{}, workerName:{}]", taskId, workerName);
                    coreTaskClient.updateReplicateStatus(taskId, workerName, ReplicateStatus.RUNNING);
                    if (model.getDappType().equals(DappType.DOCKER)) {
                        MetadataResult metadataResult = dockerService.dockerRun(taskId, model.getDappName(), model.getCmd());

                        //TODO: Upload result when core is asking for
                        resultRepoClient.addResult(dockerService.getResultModelWithZip(taskId));
                    }
                    return Thread.currentThread().getName();
                }
                , executor).thenAccept(s -> {

            log.info("Update replicate status to COMPLETED [taskId:{}, workerName:{}]", taskId, workerName);
            coreTaskClient.updateReplicateStatus(taskId, workerName, ReplicateStatus.COMPLETED);
        });
    }
}
