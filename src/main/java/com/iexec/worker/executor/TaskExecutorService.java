package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;


/*
 * this service is only caller by ReplicateDemandService when getting new replicate
 * or by AmnesiaRecoveryService when recovering an interrupted task
 */
@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private DatasetService datasetService;
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomFeignClient customFeignClient;
    private RevealService revealService;
    private WorkerConfigurationService workerConfigurationService;
    private IexecHubService iexecHubService;
    private SmsService smsService;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(DatasetService datasetService,
                               DockerComputationService dockerComputationService,
                               ResultService resultService,
                               ContributionService contributionService,
                               CustomFeignClient customFeignClient,
                               RevealService revealService,
                               WorkerConfigurationService workerConfigurationService,
                               IexecHubService iexecHubService,
                               SmsService smsService) {
        this.datasetService = datasetService;
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customFeignClient = customFeignClient;
        this.revealService = revealService;
        this.customFeignClient = customFeignClient;
        this.workerConfigurationService = workerConfigurationService;
        this.iexecHubService = iexecHubService;
        this.smsService = smsService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicates() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public CompletableFuture<Void> addReplicate(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();

        return CompletableFuture.supplyAsync(() -> compute(replicateModel), executor)
                .thenApply(stdout -> resultService.saveResult(chainTaskId, replicateModel, stdout))
                .thenAccept(isSaved -> {
                    if (isSaved) contribute(contributionAuth);
                })
                .handle((res, err) -> {
                    if (err != null) {
                        err.printStackTrace();
                    }
                    return res;
                });
    }

    @Async
    private String compute(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();
        String stdout = "";

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Task not initialized onchain yet [ChainTaskId:{}]", chainTaskId);
            throw new IllegalArgumentException("Task not initialized onchain yet");
        }

        // if (TeeEnabled && no Tee supported) => return;
        boolean doesTaskNeedTee = !contributionAuth.getEnclaveChallenge().equals(BytesUtils.EMPTY_ADDRESS);
        if (doesTaskNeedTee && !workerConfigurationService.isTeeEnabled()) {
            throw new UnsupportedOperationException("Task needs TEE, I don't support it");
        }

        // check app type
        customFeignClient.updateReplicateStatus(chainTaskId, RUNNING);
        if (!replicateModel.getAppType().equals(DappType.DOCKER)) {
            stdout = "Application is not of type Docker";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        // pull app
        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
        boolean isAppDownloaded = dockerComputationService.dockerPull(chainTaskId, replicateModel.getAppUri());
        if (!isAppDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
            stdout = "Failed to pull application image, URI:" + replicateModel.getAppUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADED);

        // pull data
        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADING);
        boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, replicateModel.getDatasetUri());
        if (!isDatasetDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
            stdout = "Failed to pull dataset, URI:" + replicateModel.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADED);

        boolean isFetched = smsService.fetchTaskSecrets(contributionAuth);
        if (!isFetched) {
            log.warn("No secrets fetched for this task, continuing [chainTaskId:{}]:", chainTaskId);
        }

        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTING);

        // decrypt data
        boolean isDatasetDecryptionNeeded = datasetService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = datasetService.decryptDataset(chainTaskId, replicateModel.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            customFeignClient.updateReplicateStatus(chainTaskId, COMPUTE_FAILED);
            stdout = "Failed to decrypt dataset, URI:" + replicateModel.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        // compute
        stdout = dockerComputationService.dockerRunAndGetLogs(replicateModel);

        if (stdout.isEmpty()) {
            customFeignClient.updateReplicateStatus(chainTaskId, COMPUTE_FAILED);
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTED);
        return stdout;
    }

    @Async
    public void contribute(ContributionAuthorization contribAuth) {
        String chainTaskId = contribAuth.getChainTaskId();
        String deterministHash = resultService.getDeterministHashFromFile(chainTaskId);
        Optional<Signature> oEnclaveSignature = resultService.getEnclaveSignatureFromFile(chainTaskId);

        if (deterministHash.isEmpty()) {
            return;
        }

        Signature enclaveSignature = SignatureUtils.emptySignature();

        if (oEnclaveSignature.isPresent()) {
            enclaveSignature = contributionService.getEnclaveSignature(contribAuth, deterministHash, oEnclaveSignature.get());
        }

        Optional<ReplicateStatus> canContributeStatus = contributionService.getCanContributeStatus(chainTaskId);
        if (!canContributeStatus.isPresent()) {
            log.error("canContributeStatus should not be empty (getChainTask issue) [chainTaskId:{}]", chainTaskId);
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, canContributeStatus.get());
        if (!canContributeStatus.get().equals(CAN_CONTRIBUTE) & enclaveSignature != null) {
            log.warn("Cant contribute [chainTaskId:{}, status:{}]", chainTaskId, canContributeStatus.get());
            return;
        }

        if (!contributionService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTING);

        Optional<ChainReceipt> oChainReceipt = contributionService.contribute(contribAuth, deterministHash, enclaveSignature);
        if (!oChainReceipt.isPresent()) {
            ChainReceipt chainReceipt = new ChainReceipt(iexecHubService.getLatestBlockNumber(), "");
            customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTE_FAILED,
                    ReplicateDetails.builder().chainReceipt(chainReceipt).build());
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTED,
                ReplicateDetails.builder().chainReceipt(oChainReceipt.get()).build());
    }

    @Async
    public void reveal(String chainTaskId) {
        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        if (!revealService.canReveal(chainTaskId)) {
            log.warn("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_REVEAL);
        }

        if (!revealService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        customFeignClient.updateReplicateStatus(chainTaskId, REVEALING);

        Optional<ChainReceipt> optionalChainReceipt = revealService.reveal(chainTaskId);
        if (!optionalChainReceipt.isPresent()) {
            ChainReceipt chainReceipt = new ChainReceipt(iexecHubService.getLatestBlockNumber(), "");
            customFeignClient.updateReplicateStatus(chainTaskId, REVEAL_FAILED,
                    ReplicateDetails.builder().chainReceipt(chainReceipt).build());
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, REVEALED,
                ReplicateDetails.builder().chainReceipt(optionalChainReceipt.get()).build());
    }

    @Async
    public void abortConsensusReached(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONSENSUS_REACHED);
    }

    @Async
    public void abortContributionTimeout(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONTRIBUTION_TIMEOUT);
    }

    @Async
    public void uploadResult(String chainTaskId) {
        customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADING);

        boolean isResultEncryptionNeeded = resultService.isResultEncryptionNeeded(chainTaskId);
        boolean isResultEncrypted = false;

        if (isResultEncryptionNeeded) {
            isResultEncrypted = resultService.encryptResult(chainTaskId);
        }

        if (isResultEncryptionNeeded && !isResultEncrypted) {
            customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
            log.error("Failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return;
        }

        String resultLink = resultService.uploadResult(chainTaskId);

        if (resultLink.isEmpty()) {
            log.error("ResultLink missing (aborting) [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
            return;
        }

        String callbackData = resultService.getCallbackDataFromFile(chainTaskId);

        log.info("Uploaded result with details [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();

        customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADED, details);
    }

    @Async
    public void completeTask(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, COMPLETED);
    }
}