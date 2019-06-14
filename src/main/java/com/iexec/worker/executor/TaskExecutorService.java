package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.chain.Web3jService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import org.apache.commons.lang3.tuple.Pair;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
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
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomFeignClient customFeignClient;
    private RevealService revealService;
    private WorkerConfigurationService workerConfigurationService;
    private IexecHubService iexecHubService;
    private Web3jService web3jService;
    private PublicConfigurationService publicConfigurationService;
    private ComputationService computationService;
    private SconeTeeService sconeTeeService;

    // internal variables
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;
    private String corePublicAddress;

    //TODO make this fat constructor lose weight
    public TaskExecutorService(DatasetService datasetService,
                               ResultService resultService,
                               ContributionService contributionService,
                               CustomFeignClient customFeignClient,
                               RevealService revealService,
                               WorkerConfigurationService workerConfigurationService,
                               IexecHubService iexecHubService,
                               Web3jService web3jService,
                               ComputationService computationService,
                               SconeTeeService sconeTeeService,
                               PublicConfigurationService publicConfigurationService) {
        this.datasetService = datasetService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customFeignClient = customFeignClient;
        this.revealService = revealService;
        this.workerConfigurationService = workerConfigurationService;
        this.iexecHubService = iexecHubService;
        this.web3jService = web3jService;
        this.computationService = computationService;
        this.sconeTeeService = sconeTeeService;
        this.publicConfigurationService = publicConfigurationService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    @PostConstruct
    public void initIt() {
        corePublicAddress = publicConfigurationService.getSchedulerPublicAddress();
    }

    public boolean canAcceptMoreReplicates() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public CompletableFuture<Boolean> addReplicate(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();

        Optional<TaskDescription> taskDescriptionFromChain = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        // don't compute if task needs TEE && TEE not supported;
        boolean isTeeTask = TeeUtils.isTeeTag(contributionAuth.getEnclaveChallenge());
        if (isTeeTask && !workerConfigurationService.isTeeEnabled()) {
            log.error("Task needs TEE, I don't support it [chainTaskId:{}]", chainTaskId);
            return CompletableFuture.completedFuture(false);
        }        

        // don't compute if task is not initialized onChain
        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Task not initialized onChain [chainTaskId:{}]", chainTaskId);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> compute(contributionAuth, isTeeTask), executor)
                .thenApply(stdout -> resultService.saveResult(chainTaskId, taskDescriptionFromChain.get(), stdout))
                .thenAccept(isSaved -> { if (isSaved) contribute(contributionAuth, isTeeTask); })
                .handle((res, err) -> {
                    if (err != null) err.printStackTrace();
                    return err == null;
                });
    }


    public void tryToContribute(ContributionAuthorization contributionAuth) {

        String chainTaskId = contributionAuth.getChainTaskId();

        if (!contributionService.isContributionAuthorizationValid(contributionAuth, corePublicAddress)) {
            log.error("The contribution contribAuth is NOT valid, the task will not be performed"
                    + " [chainTaskId:{}, contribAuth:{}]", chainTaskId, contributionAuth);
            return;
        }

        boolean isResultAvailable = resultService.isResultAvailable(chainTaskId);

        if (!isResultAvailable) {
            log.info("Result not found, will restart task from RUNNING [chainTaskId:{}]", chainTaskId);
            addReplicate(contributionAuth);
        } else {
            log.info("Result found, will restart task from CONTRIBUTING [chainTaskId:{}]", chainTaskId);
            boolean isTeeTask = TeeUtils.isTeeTag(contributionAuth.getEnclaveChallenge());
            contribute(contributionAuth, isTeeTask);
        }
    }

    @Async
    private String compute(ContributionAuthorization contributionAuth, boolean isTeeTask) {
        String chainTaskId = contributionAuth.getChainTaskId();
        String message = "";

        Optional<TaskDescription> taskDescriptionFromChain = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (!taskDescriptionFromChain.isPresent()) {
            message = "TaskDescription not found";
            log.error(message + " [chainTaskId:{}]", chainTaskId);
            return message;
        }

        TaskDescription taskDescription = taskDescriptionFromChain.get();
        customFeignClient.updateReplicateStatus(chainTaskId, RUNNING);

        // check app type
        if (!taskDescription.getAppType().equals(DappType.DOCKER)) {
            message = "Application is not of type Docker";
            log.error(message + " [chainTaskId:{}]", chainTaskId);
            return message;
        }

        // Try to downloadApp
        String errorDwnlApp = tryToDownloadApp(taskDescription);
        if (!errorDwnlApp.isEmpty()) {
            return errorDwnlApp;
        }
        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADED);
 
        // Try to downloadData
        String errorDwnlData = tryToDownloadData(taskDescription);
        if (!errorDwnlData.isEmpty()) {
            return errorDwnlData;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADED);
        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTING);

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) {
            return error;
        }

        Pair<ReplicateStatus, String> pair = null;
        if (isTeeTask) {
            pair = computationService.runTeeComputation(taskDescription, contributionAuth);
        } else {
            pair = computationService.runNonTeeComputation(taskDescription, contributionAuth);
        }

        if (pair == null) pair = Pair.of(COMPUTE_FAILED, "Error while computing");

        customFeignClient.updateReplicateStatus(chainTaskId, pair.getLeft());
        return pair.getRight();
    }

    private String tryToDownloadApp(TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) {
            return error;
        }

        // pull app
        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
        boolean isAppDownloaded = computationService.downloadApp(chainTaskId, taskDescription.getAppUri());
        if (!isAppDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
            String errorMessage = "Failed to pull application image, URI:" + taskDescription.getAppUri();
            log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
            return errorMessage;
        }

        return "";
    }

    private String tryToDownloadData(TaskDescription taskDescription) {
        String chainTaskId = taskDescription.getChainTaskId();

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) {
            return error;
        }

        // pull data
        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADING);
        boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, taskDescription.getDatasetUri());
        if (!isDatasetDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
            String errorMessage = "Failed to pull dataset, URI:" + taskDescription.getDatasetUri();
            log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
            return errorMessage;
        }

        return "";
    }

    private String checkContributionAbility(String chainTaskId) {
        String errorMessage = "";

        Optional<ReplicateStatus> contributionStatus = contributionService.getCanContributeStatus(chainTaskId);
        if(contributionStatus.isPresent() && !contributionStatus.get().equals(CAN_CONTRIBUTE)) {
            errorMessage = "The worker cannot contribute";
            log.error(errorMessage + " [chainTaskId:{}, replicateStatus:{}]", chainTaskId, contributionStatus.get());
            customFeignClient.updateReplicateStatus(chainTaskId, contributionStatus.get());
            return errorMessage;
        }

        return errorMessage;
    }

    @Async
    public void contribute(ContributionAuthorization contribAuth, boolean isTeeTask) {
        String chainTaskId = contribAuth.getChainTaskId();

        String deterministHash = resultService.getDeterministHashForTask(chainTaskId);
        if (deterministHash.isEmpty()) {
            log.error("Cannot contribute, determinism hash is empty [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId,
                    ReplicateStatus.CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND);
            return;
        }

        Signature enclaveSignature = SignatureUtils.emptySignature();

        if (isTeeTask) {

            Optional<Signature> oSconeEnclaveSignature = sconeTeeService.getSconeEnclaveSignatureFromFile(chainTaskId);
            if (!oSconeEnclaveSignature.isPresent()) {
                log.error("Cannot contribute, could not get enclave signature [chainTaskId:{}]", chainTaskId);
                customFeignClient.updateReplicateStatus(chainTaskId,
                        ReplicateStatus.CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED);
                return;
            }

            enclaveSignature = oSconeEnclaveSignature.get();

            boolean isValid = sconeTeeService.isEnclaveSignatureValid(chainTaskId, deterministHash,
                    enclaveSignature, contribAuth.getEnclaveChallenge());

            if (!isValid) {
                log.error("Cannot contribute, enclave signature is not valid [chainTaskId:{}]", chainTaskId);
                customFeignClient.updateReplicateStatus(chainTaskId,
                        ReplicateStatus.CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED);
                return;
            }
        }

        Optional<ReplicateStatus> oCannotContributeStatus = contributionService.getCannotContributeStatus(chainTaskId);
        if (oCannotContributeStatus.isPresent()) {
            log.error("Cannot contribute [chainTaskId:{}, cause:{}]", chainTaskId, oCannotContributeStatus.get());
            customFeignClient.updateReplicateStatus(chainTaskId, oCannotContributeStatus.get());
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, ReplicateStatus.CAN_CONTRIBUTE);

        if (!contributionService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                    contribAuth.getWorkerWallet());
            LoggingUtils.printHighlightedMessage(noEnoughGas);
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

        if (oChainReceipt.get().getBlockNumber() == 0) {
            log.warn("The blocknumber of the receipt is equal to 0, the CONTRIBUTED status will not be " +
                    "sent to the core [chainTaskId:{}]", chainTaskId);
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTED,
                ReplicateDetails.builder().chainReceipt(oChainReceipt.get()).build());
    }

    @Async
    public void reveal(String chainTaskId, long consensusBlock) {
        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        if (!web3jService.isBlockAvailable(consensusBlock)) {
            log.warn("Sync issues before canReveal (latestBlock before consensusBlock) [chainTaskId:{}, latestBlock:{}, " +
                    "consensusBlock:{}]", chainTaskId, web3jService.getLatestBlockNumber(), consensusBlock);
            return;
        }

        if (!revealService.canReveal(chainTaskId)) {
            log.warn("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_REVEAL);
            return;
        }

        if (!revealService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                    workerConfigurationService.getWorkerWalletAddress());
            LoggingUtils.printHighlightedMessage(noEnoughGas);
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

        if (optionalChainReceipt.get().getBlockNumber() == 0) {
            log.warn("The blocknumber of the receipt is equal to 0, the REVEALED status will not be " +
                    "sent to the core [chainTaskId:{}]", chainTaskId);
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