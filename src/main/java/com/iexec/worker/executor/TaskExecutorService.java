package com.iexec.worker.executor;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private CoreTaskClient coreTaskClient;
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(CoreTaskClient coreTaskClient,
                               DockerComputationService dockerComputationService,
                               ContributionService contributionService,
                               ResultService resultService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() / 2;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicate() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public void addReplicate(AvailableReplicateModel model) {
        ContributionAuthorization contribAuth = model.getContributionAuthorization();

        CompletableFuture.supplyAsync(() -> executeTask(model), executor)
                .thenAccept(metadataResult -> tryToContribute(contribAuth, metadataResult));
    }

    private MetadataResult executeTask(AvailableReplicateModel model) {
        String walletAddress = model.getContributionAuthorization().getWorkerWallet();
        String chainTaskId = model.getContributionAuthorization().getChainTaskId();

        if (contributionService.isChainTaskInitialized(chainTaskId)) {
            log.info("Task, initialized, update replicate status to RUNNING [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.RUNNING);

            if (model.getDappType().equals(DappType.DOCKER)) {
                try {
                    MetadataResult metadataResult = dockerComputationService.dockerRun(chainTaskId, model.getDappName(), model.getCmd());
                    //save metadataResult (without zip payload) in memory
                    resultService.addMetaDataResult(chainTaskId, metadataResult);
                    log.info("Computation finished, update replicate status to COMPUTED [chainTaskId:{}, walletAddress:{}]",
                            chainTaskId, walletAddress);
                    coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.COMPUTED);

                    return metadataResult;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log.warn("The task has NOT been initialized on chain [chainTaskId:{}]", chainTaskId);
        }
        return new MetadataResult();
    }

    private void tryToContribute(ContributionAuthorization contribAuth, MetadataResult metadataResult) {
        String walletAddress = contribAuth.getWorkerWallet();
        String chainTaskId = contribAuth.getChainTaskId();

        if (!contributionService.canContribute(chainTaskId)) {
            log.warn("The worker cannot contribute since the contribution wouldn't be valid [chainTaskId:{}, " +
                    "walletAddress:{}", chainTaskId, walletAddress);
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.ERROR);
            return;
        }

        log.info("The worker is CONTRIBUTING [chainTaskId:{}, walletAddress:{}, deterministHash:{}]",
                chainTaskId, walletAddress, metadataResult.getDeterministHash());
        coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTING);
        if (contributionService.contribute(contribAuth, metadataResult.getDeterministHash())) {
            log.info("The worker has contributed successfully, update replicate status to CONTRIBUTED [chainTaskId:{}, " +
                            "walletAddress:{}, deterministHash:{}]",
                    chainTaskId, walletAddress, metadataResult.getDeterministHash());
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTED);
        } else {
            log.warn("The worker couldn't contribute, update replicate status to CONTRIBUTE_FAILED [chainTaskId:{}, walletAddress:{}, deterministHash:{}]",
                    chainTaskId, walletAddress, metadataResult.getDeterministHash());
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTE_FAILED);
        }

    }
}
