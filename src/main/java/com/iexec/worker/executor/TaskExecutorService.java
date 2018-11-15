package com.iexec.worker.executor;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
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
    private IexecHubService iexecHubService;

    public TaskExecutorService(CoreTaskClient coreTaskClient,
                               DockerComputationService dockerComputationService,
                               ResultRepoClient resultRepoClient,
                               ResultService resultService,
                               IexecHubService iexecHubService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerComputationService = dockerComputationService;
        this.resultRepoClient = resultRepoClient;
        this.resultService = resultService;
        this.iexecHubService = iexecHubService;
        maxNbExecutions = Runtime.getRuntime().availableProcessors() / 2;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicate() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public void addReplicate(AvailableReplicateModel model) {
        ContributionAuthorization contribAuth = model.getContributionAuthorization();
        String walletAddress = contribAuth.getWorkerWallet();
        String chainTaskId = contribAuth.getChainTaskId();

        // TODO: ugly here, that should be changed
        CompletableFuture.supplyAsync(() -> executeTask(model), executor).thenAccept(metadataResult -> {

            // contribute should happen here
            try {
                log.info("Worker trying to contribute [chainTaskId:{}, walletAddress:{}, consensusHash:{}]",
                        chainTaskId, walletAddress, metadataResult.getConsensusHash());

                if (iexecHubService.contribute(contribAuth, metadataResult.getConsensusHash())){
                    log.info("The worker has contributed successfully [chainTaskId:{}, walletAddress:{}, consensusHash:{}]",
                            chainTaskId, walletAddress, metadataResult.getConsensusHash());
                    log.info("Update replicate status to COMPUTED [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
                    coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.COMPUTED);
                } else {
                    log.warn("The worker couldn't contribute [chainTaskId:{}, walletAddress:{}, consensusHash:{}]",
                            chainTaskId, walletAddress, metadataResult.getConsensusHash());
                }
            } catch (Exception e) {
                log.warn("Contribution of the worker has failed [chainTaskId:{}, walletAddress:{}, consensusHash:{}]",
                        chainTaskId, walletAddress, metadataResult.getConsensusHash());
                e.printStackTrace();
            }
        });
    }

    public MetadataResult executeTask(AvailableReplicateModel model) {
        String walletAddress = model.getWorkerAddress();
        String chainTaskId = model.getChainTaskId();

        if (iexecHubService.isTaskInitialized(chainTaskId)) {
            // TODO: this part should be refactored
            log.info("Update replicate status to RUNNING [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.RUNNING);
            if (model.getDappType().equals(DappType.DOCKER)) {
                MetadataResult metadataResult = dockerComputationService.dockerRun(chainTaskId, model.getDappName(), model.getCmd());
                resultService.addMetaDataResult(chainTaskId, metadataResult); //save metadataResult (without zip payload) in memory
                log.info("Consensus Hash has been computed [chainTaskId:{}, consensusHash:{}]", chainTaskId, metadataResult.getConsensusHash());
                return metadataResult;
            }
        } else {
            log.warn("The task has NOT been initialized on chain [chainTaskId:{}]", chainTaskId);
        }
        return new MetadataResult();
    }
}
