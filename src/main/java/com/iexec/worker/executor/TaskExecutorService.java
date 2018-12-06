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
import com.iexec.worker.security.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private CoreTaskClient coreTaskClient;
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;
    private TokenService tokenService;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(CoreTaskClient coreTaskClient,
                               DockerComputationService dockerComputationService,
                               ContributionService contributionService,
                               ResultService resultService,
                               TokenService tokenService) {
        this.coreTaskClient = coreTaskClient;
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.tokenService = tokenService;

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
        String token = tokenService.getToken();

        if (contributionService.isChainTaskInitialized(chainTaskId)) {
            log.info("RUNNING [chainTaskId:{}]", chainTaskId);
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, RUNNING, token);

            if (model.getDappType().equals(DappType.DOCKER)) {
                try {
                    log.info("APP_DOWNLOADING [chainTaskId:{}]", chainTaskId);
                    coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, APP_DOWNLOADING, token);
                    boolean isImagePulled = dockerComputationService.dockerPull(chainTaskId, model.getDappName());
                    if (isImagePulled){
                        log.info("APP_DOWNLOADED [chainTaskId:{}]", chainTaskId);
                        coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, APP_DOWNLOADED, token);
                    } else {
                        log.info("APP_DOWNLOAD_FAILED [chainTaskId:{}]", chainTaskId);
                        coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, APP_DOWNLOAD_FAILED, token);
                    }

                    log.info("COMPUTING [chainTaskId:{}]", chainTaskId);
                    coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, COMPUTING, token);
                    MetadataResult metadataResult = dockerComputationService.dockerRun(chainTaskId, model.getDappName(), model.getCmd());
                    //save metadataResult (without zip payload) in memory
                    resultService.addMetaDataResult(chainTaskId, metadataResult);
                    log.info("COMPUTED [chainTaskId:{}]", chainTaskId);
                    coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, COMPUTED, token);

                    return metadataResult;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            log.warn("Task NOT initialized on chain [chainTaskId:{}]", chainTaskId);
        }
        return new MetadataResult();
    }

    private void tryToContribute(ContributionAuthorization contribAuth, MetadataResult metadataResult) {
        String walletAddress = contribAuth.getWorkerWallet();
        String chainTaskId = contribAuth.getChainTaskId();
        String token = tokenService.getToken();

        if (!contributionService.canContribute(chainTaskId)) {
            log.warn("The worker cannot contribute since the contribution wouldn't be valid [chainTaskId:{}, " +
                    "walletAddress:{}", chainTaskId, walletAddress);
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.ERROR, token);
            return;
        }

        log.info("CONTRIBUTING [chainTaskId:{}, walletAddress:{}, deterministHash:{}]",
                chainTaskId, walletAddress, metadataResult.getDeterministHash());
        coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTING, token);
        if (contributionService.contribute(contribAuth, metadataResult.getDeterministHash())) {
            log.info("CONTRIBUTED [chainTaskId:{}, walletAddress:{}, deterministHash:{}]",
                    chainTaskId, walletAddress, metadataResult.getDeterministHash());
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTED, token);
        } else {
            log.warn("CONTRIBUTE_FAILED [chainTaskId:{}, walletAddress:{}, deterministHash:{}]",
                    chainTaskId, walletAddress, metadataResult.getDeterministHash());
            coreTaskClient.updateReplicateStatus(chainTaskId, walletAddress, ReplicateStatus.CONTRIBUTE_FAILED, token);
        }

    }
}
