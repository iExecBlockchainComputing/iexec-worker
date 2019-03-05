package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.security.TeeSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private DatasetService datasetService;
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomFeignClient feignClient;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(DatasetService datasetService,
                               DockerComputationService dockerComputationService,
                               ContributionService contributionService,
                               ResultService resultService,
                               CustomFeignClient feignClient) {
        this.datasetService = datasetService;
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.feignClient = feignClient;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicate() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public void addReplicate(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contribAuth = replicateModel.getContributionAuthorization();

        CompletableFuture.supplyAsync(() -> executeTask(replicateModel), executor)
                .thenAccept(isExecuted -> {
                    if (!isExecuted) {
                        return;
                    }
                    String deterministHash = resultService.getDeterministHashFromFile(contribAuth.getChainTaskId());
                    Optional<TeeSignature.Sign> enclaveSignature = resultService.getEnclaveSignatureFromFile(contribAuth.getChainTaskId());
                    tryToContribute(contribAuth, deterministHash, enclaveSignature);
                });
    }

    private boolean executeTask(AvailableReplicateModel replicateModel) {
        String chainTaskId = replicateModel.getContributionAuthorization().getChainTaskId();
        String stdout = "";

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("task NOT initialized onchain [chainTaskId:{}]", chainTaskId);
            return false;
        }

        // check app type
        feignClient.updateReplicateStatus(chainTaskId, RUNNING);
        if (!replicateModel.getAppType().equals(DappType.DOCKER)) {
            log.error("app is not of type Docker [chainTaskId:{}]", chainTaskId);
            stdout = "Application is not of type Docker";
            resultService.saveResult(chainTaskId, replicateModel, stdout);
            return true;
        }

        // pull app
        feignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
        boolean isAppDownloaded = dockerComputationService.dockerPull(chainTaskId, replicateModel.getAppUri());
        if (!isAppDownloaded) {
            stdout = String.format("Failed to pull application image [URI:{}]", replicateModel.getAppUri());
            log.info(stdout);
            feignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
            resultService.saveResult(chainTaskId, replicateModel, stdout);
            return true;
        }

        feignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADED);

        // pull data
        feignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADING);
        boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, replicateModel.getDatasetUri());
        if (!isDatasetDownloaded) {
            stdout = String.format("Failed to pull dataset [URI:%s]", replicateModel.getDatasetUri());
            log.info(stdout);
            feignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
            resultService.saveResult(chainTaskId, replicateModel, stdout);
            return true;
        }

        feignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADED);

        // compute
        feignClient.updateReplicateStatus(chainTaskId, COMPUTING);
        stdout = dockerComputationService.dockerRunAndGetLogs(replicateModel);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.info(stdout);
            feignClient.updateReplicateStatus(chainTaskId, COMPUTE_FAILED);
            resultService.saveResult(chainTaskId, replicateModel, stdout);
            return true;
        }

        feignClient.updateReplicateStatus(chainTaskId, COMPUTED);
        resultService.saveResult(chainTaskId, replicateModel, stdout);
        return true;
    }

    private void tryToContribute(ContributionAuthorization contribAuth, String deterministHash, Optional<TeeSignature.Sign> enclaveSignature) {
        if (deterministHash.isEmpty()) {
            return;
        }
        String chainTaskId = contribAuth.getChainTaskId();
        Sign.SignatureData enclaveSignatureData = contributionService.getEnclaveSignatureData(contribAuth, deterministHash, enclaveSignature);

        Optional<ReplicateStatus> canContributeStatus = contributionService.getCanContributeStatus(chainTaskId);
        if (!canContributeStatus.isPresent()) {
            log.error("canContributeStatus should not be empty (getChainTask issue) [chainTaskId:{}]", chainTaskId);
            return;
        }

        feignClient.updateReplicateStatus(chainTaskId, canContributeStatus.get());
        if (!canContributeStatus.get().equals(CAN_CONTRIBUTE) & enclaveSignatureData != null) {
            log.warn("Cant contribute [chainTaskId:{}, status:{}]", chainTaskId, canContributeStatus.get());
            return;
        }

        if (!contributionService.hasEnoughGas()) {
            feignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        feignClient.updateReplicateStatus(chainTaskId, CONTRIBUTING);

        ChainReceipt chainReceipt = contributionService.contribute(contribAuth, deterministHash, enclaveSignatureData);
        if (chainReceipt == null) {
            feignClient.updateReplicateStatus(chainTaskId, CONTRIBUTE_FAILED);
            return;
        }

        feignClient.updateReplicateStatus(chainTaskId, CONTRIBUTED, chainReceipt);
    }


}
