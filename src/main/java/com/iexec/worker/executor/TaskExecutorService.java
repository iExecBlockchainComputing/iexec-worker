package com.iexec.worker.executor;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
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
    private DockerComputationService dockerComputationService;
    private ResultRepoClient resultRepoClient;
    private ResultService resultService;
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(CoreTaskClient coreTaskClient,
                               DockerComputationService dockerComputationService,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerComputationService = dockerComputationService;
        this.resultRepoClient = resultRepoClient;
        this.resultService = resultService;
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
                        MetadataResult metadataResult = dockerComputationService.dockerRun(taskId, model.getDappName(), model.getCmd());
                        resultService.addMetaDataResult(taskId, metadataResult);//save metadataResult (without zip payload) in memory
                    }
                    return Thread.currentThread().getName();
                }
                , executor).thenAccept(s -> {

            log.info("Update replicate status to COMPUTED [taskId:{}, workerName:{}]", taskId, workerName);
            coreTaskClient.updateReplicateStatus(taskId, workerName, ReplicateStatus.COMPUTED);
        });
    }
}
