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
import com.iexec.worker.tee.scone.SconeEnclaveSignature;
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
public class TaskExecutorHelperService {

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
    public TaskExecutorHelperService(DatasetService datasetService,
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

    // Pair<TaskDescription, String> tryToGetTaskDescription(String chainTaskId) {
    //     Optional<TaskDescription> oTaskDescription = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

    //     if (oTaskDescription.isPresent()) return Pair.of(oTaskDescription.get(), "");

    //     String errorMessage = "TaskDescription not found onChain";
    //     log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
    //     return Pair.of(null, errorMessage);
    // }

    // String tryToDownloadApp(TaskDescription taskDescription) {
    //     String chainTaskId = taskDescription.getChainTaskId();

    //     String error = checkContributionAbility(chainTaskId);
    //     if (!error.isEmpty()) {
    //         return error;
    //     }

    //     // pull app
    //     customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
    //     boolean isAppDownloaded = computationService.downloadApp(chainTaskId, taskDescription.getAppUri());
    //     if (!isAppDownloaded) {
    //         customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
    //         String errorMessage = "Failed to pull application image, URI:" + taskDescription.getAppUri();
    //         log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
    //         return errorMessage;
    //     }

    //     return "";
    // }

    // String tryToDownloadData(TaskDescription taskDescription) {
    //     String chainTaskId = taskDescription.getChainTaskId();

    //     String error = checkContributionAbility(chainTaskId);
    //     if (!error.isEmpty()) {
    //         return error;
    //     }

    //     // pull data
    //     customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADING);
    //     boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, taskDescription.getDatasetUri());
    //     if (!isDatasetDownloaded) {
    //         customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
    //         String errorMessage = "Failed to pull dataset, URI:" + taskDescription.getDatasetUri();
    //         log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
    //         return errorMessage;
    //     }

    //     return "";
    // }

    // String checkContributionAbility(String chainTaskId) {
    //     String errorMessage = "";

    //     Optional<ReplicateStatus> oCannotContributeStatus = contributionService.getCannotContributeStatus(chainTaskId);
    //     if (oCannotContributeStatus.isPresent()) {
    //         errorMessage = "The worker cannot contribute";
    //         log.error(errorMessage + " [chainTaskId:{}, cause:{}]", chainTaskId, oCannotContributeStatus.get());
    //         customFeignClient.updateReplicateStatus(chainTaskId, oCannotContributeStatus.get());
    //         return errorMessage;
    //     }

    //     return errorMessage;
    // }

    String getDeterministHash(String chainTaskId) {
        String deterministHash = resultService.getDeterministHashForTask(chainTaskId);
        if (deterministHash.isEmpty()) {
            log.error("Cannot contribute, determinism hash is empty [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId,
                    ReplicateStatus.CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND);
            return "";
        }

        return deterministHash;
    }

    Optional<Signature> getEnclaveSignature(String chainTaskId, boolean isTeeTask,
                                            String deterministHash, String signerAddress) {

        if (!isTeeTask) return Optional.of(SignatureUtils.emptySignature());

        Optional<SconeEnclaveSignature> oSconeEnclaveSignature = sconeTeeService.readSconeEnclaveSignatureFile(chainTaskId);
        if (!oSconeEnclaveSignature.isPresent()) {
            log.error("Cannot contribute, problem reading and parsing enclaveSig.iexec file [chainTaskId:{}]", chainTaskId);
            log.error("Cannot contribute, TEE execution not verified [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId,
                    ReplicateStatus.CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED);
            return Optional.empty();
        }

        SconeEnclaveSignature sconeEnclaveSignature = oSconeEnclaveSignature.get();
        log.debug("EnclaveSig.iexec file content [chainTaskId:{}, enclaveSig.iexec:{}]",
                chainTaskId, sconeEnclaveSignature);

        Signature enclaveSignature = new Signature(sconeEnclaveSignature.getSignature());
        String resultHash = sconeEnclaveSignature.getResultHash();
        String resultSeal = sconeEnclaveSignature.getResultSalt();

        boolean isValid = sconeTeeService.isEnclaveSignatureValid(resultHash, resultSeal,
                enclaveSignature, signerAddress);

        if (!isValid) {
            log.error("Scone enclave signature is not valid [chainTaskId:{}]", chainTaskId);
            log.error("Cannot contribute, TEE execution not verified [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId,
                    ReplicateStatus.CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED);
            return Optional.empty();
        }

        return Optional.of(enclaveSignature);
    }

    boolean shouldContribute(String chainTaskId) {
        Optional<ReplicateStatus> oCannotContributeStatus = contributionService.getCannotContributeStatus(chainTaskId);

        if (!oCannotContributeStatus.isPresent()) return true;

        log.error("Cannot contribute [chainTaskId:{}, cause:{}]", chainTaskId, oCannotContributeStatus.get());
        customFeignClient.updateReplicateStatus(chainTaskId, oCannotContributeStatus.get());
        return false;
    }

    boolean checkGasBalance(String chainTaskId) {
        if (contributionService.hasEnoughGas()) return true;

        customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
        String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                workerConfigurationService.getWorkerWalletAddress());
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    // boolean isValidChainReceipt(String chainTaskId, Optional<ChainReceipt> oChainReceipt) {
    //     if (!oChainReceipt.isPresent()) {
    //         ChainReceipt chainReceipt = new ChainReceipt(iexecHubService.getLatestBlockNumber(), "");
    //         customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTE_FAILED,
    //                 ReplicateDetails.builder().chainReceipt(chainReceipt).build());
    //         return;
    //     }

    //     if (oChainReceipt.get().getBlockNumber() == 0) {
    //         log.warn("The blocknumber of the receipt is equal to 0, the CONTRIBUTED status will not be " +
    //                 "sent to the core [chainTaskId:{}]", chainTaskId);
    //         return;
    //     }
    // }
}