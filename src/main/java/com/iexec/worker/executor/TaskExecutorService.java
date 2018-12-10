package com.iexec.worker.executor;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.replicate.UpdateReplicateStatusService;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;
    private UpdateReplicateStatusService replicateStatusService;
    private IexecHubService iexecHubService;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(DockerComputationService dockerComputationService,
                               ContributionService contributionService,
                               ResultService resultService,
                               UpdateReplicateStatusService replicateStatusService,
                               IexecHubService iexecHubService) {
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.replicateStatusService = replicateStatusService;
        this.iexecHubService = iexecHubService;

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
        String chainTaskId = model.getContributionAuthorization().getChainTaskId();

        if (contributionService.isChainTaskInitialized(chainTaskId)) {
            replicateStatusService.updateReplicateStatus(chainTaskId, RUNNING);

            if (model.getDappType().equals(DappType.DOCKER)) {

                replicateStatusService.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
                boolean isImagePulled = dockerComputationService.dockerPull(chainTaskId, model.getDappName());
                if (isImagePulled) {
                    replicateStatusService.updateReplicateStatus(chainTaskId, APP_DOWNLOADED);
                } else {
                    replicateStatusService.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
                }

                replicateStatusService.updateReplicateStatus(chainTaskId, COMPUTING);
                try {
                    MetadataResult metadataResult = dockerComputationService.dockerRun(chainTaskId, model.getDappName(), model.getCmd());
                    //save metadataResult (without zip payload) in memory
                    resultService.addMetaDataResult(chainTaskId, metadataResult);
                    replicateStatusService.updateReplicateStatus(chainTaskId, COMPUTED);
                    return metadataResult;
                } catch (Exception e) {
                    log.error("Error in the run of the application [error:{}]", e.getMessage());
                }
            }
        } else {
            log.warn("Task NOT initialized on chain [chainTaskId:{}]", chainTaskId);
        }
        return new MetadataResult();
    }

    private void tryToContribute(ContributionAuthorization contribAuth, MetadataResult metadataResult) {
        if (metadataResult.getDeterministHash().isEmpty()) {
            return;
        }

        String walletAddress = contribAuth.getWorkerWallet();
        String chainTaskId = contribAuth.getChainTaskId();

        if (!contributionService.canContribute(chainTaskId)) {
            log.warn("The worker cannot contribute since the contribution wouldn't be valid [chainTaskId:{}, " +
                    "walletAddress:{}", chainTaskId, walletAddress);
            replicateStatusService.updateReplicateStatus(chainTaskId, ERROR);
            return;
        }

        replicateStatusService.updateReplicateStatus(chainTaskId, CONTRIBUTING);
        if (contributionService.contribute(contribAuth, metadataResult.getDeterministHash())) {
            replicateStatusService.updateReplicateStatus(chainTaskId, CONTRIBUTED);
        } else {
            replicateStatusService.updateReplicateStatus(chainTaskId, CONTRIBUTE_FAILED);
        }
    }
}
