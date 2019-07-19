package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeEnclaveSignatureFile;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.DETERMINISM_HASH_NOT_FOUND;
import static com.iexec.common.replicate.ReplicateStatusCause.TEE_EXECUTION_NOT_VERIFIED;


/*
 * this service is only caller by ReplicateDemandService when getting new replicate
 * or by AmnesiaRecoveryService when recovering an interrupted task
 */
@Slf4j
@Service
public class TaskManagerService {

    private final int maxNbExecutions;
    private final ThreadPoolExecutor executor;
    private DatasetService datasetService;
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomFeignClient customFeignClient;
    private WorkerConfigurationService workerConfigurationService;
    private SconeTeeService sconeTeeService;
    private IexecHubService iexecHubService;
    private CustomDockerClient customDockerClient;
    private RevealService revealService;
    private PublicConfigurationService publicConfigurationService;
    private ComputationService computationService;
    private String corePublicAddress;
    HashMap<String, ContributionAuthorization> contributionAuthorizations = new HashMap<>();

    public TaskManagerService(DatasetService datasetService,
                              ResultService resultService,
                              ContributionService contributionService,
                              CustomFeignClient customFeignClient,
                              WorkerConfigurationService workerConfigurationService,
                              SconeTeeService sconeTeeService,
                              IexecHubService iexecHubService,
                              ComputationService computationService,
                              CustomDockerClient customDockerClient,
                              RevealService revealService,
                              PublicConfigurationService publicConfigurationService) {
        this.datasetService = datasetService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customFeignClient = customFeignClient;
        this.workerConfigurationService = workerConfigurationService;
        this.sconeTeeService = sconeTeeService;
        this.iexecHubService = iexecHubService;
        this.computationService = computationService;
        this.customDockerClient = customDockerClient;
        this.revealService = revealService;
        this.publicConfigurationService = publicConfigurationService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }



    public boolean canAcceptMoreReplicates() {
        //return executor.getActiveCount() < maxNbExecutions;
        return true;
    }

    @PostConstruct
    public void initIt() {
        corePublicAddress = publicConfigurationService.getSchedulerPublicAddress();
    }



    public boolean start(String chainTaskId, TaskNotificationExtra extra){
        ContributionAuthorization contributionAuthorization;
        if (extra == null || extra.getContributionAuthorization() == null){
            log.error("Contribution auth missing [chainTaskId:{}]", chainTaskId);
            return false;
        }

        contributionAuthorization = extra.getContributionAuthorization();

        contributionAuthorizations.putIfAbsent(chainTaskId, contributionAuthorization);

        if (!contributionService.isContributionAuthorizationValid(contributionAuthorization, corePublicAddress)) {
            log.error("The contribution contribAuth is NOT valid, the task will not be performed"
                    + " [chainTaskId:{}, contribAuth:{}]", chainTaskId, contributionAuthorization);
            return false;
        }

        // don't run if task is not initialized onChain
        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Task not initialized onChain [chainTaskId:{}]", chainTaskId);
            return false;
        }

        Optional<TaskDescription> taskDescriptionFromChain =
                iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (!taskDescriptionFromChain.isPresent()) {
            log.error("Cannot run, task description not found onChain [chainTaskId:{}]",
                    chainTaskId);
            return false;
        }

        TaskDescription taskDescription = taskDescriptionFromChain.get();
        boolean isTeeTask = taskDescription.isTeeTask();

        // don't run if task needs TEE && TEE not supported;
        if (isTeeTask && !workerConfigurationService.isTeeEnabled()) {
            log.error("Task needs TEE, I don't support it [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    boolean downloadApp(String chainTaskId) {
        Optional<TaskDescription> taskDescriptionFromChain =
                iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (taskDescriptionFromChain.isEmpty()){
            return false;
        }

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) return false;

        // pull app

        boolean isAppDownloaded = computationService.downloadApp(chainTaskId, taskDescriptionFromChain.get());
        if (!isAppDownloaded) {
            String errorMessage = "Failed to pull application image, URI:" + taskDescriptionFromChain.get().getAppUri();
            log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    boolean downloadData(String chainTaskId) {
        Optional<TaskDescription> taskDescriptionFromChain =
                iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (taskDescriptionFromChain.isEmpty()){
            return false;
        }

        String dataUri = taskDescriptionFromChain.get().getDatasetUri();

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) return false;

        // pull data

        boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, dataUri);
        if (!isDatasetDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
            String errorMessage = "Failed to pull dataset, URI:" + dataUri;
            log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    public boolean compute(String chainTaskId) {
        ContributionAuthorization contributionAuthorization = contributionAuthorizations.get(chainTaskId);

        if (contributionAuthorization == null){
            log.error("Contribution auth missing for compute [chainTaskId:{}]", chainTaskId);
            return false;
        }

        Optional<TaskDescription> taskDescriptionFromChain =
                iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (taskDescriptionFromChain.isEmpty()){
            return false;
        }

        String error = checkContributionAbility(chainTaskId);
        if (!error.isEmpty()) return false;

        boolean isTeeTask = taskDescriptionFromChain.get().isTeeTask();


        String imageExistenceError = checkIfAppImageExists(chainTaskId,
                taskDescriptionFromChain.get().getAppUri());
        if (!imageExistenceError.isEmpty()) return false;

        boolean isComputed;
        if (isTeeTask) {
            isComputed = computationService.runTeeComputation(taskDescriptionFromChain.get(), contributionAuthorization);
        } else {
            isComputed = computationService.runNonTeeComputation(taskDescriptionFromChain.get(), contributionAuthorization);
        }

        //customFeignClient.updateReplicateStatus(chainTaskId, pair.getLeft());
        //return pair.getRight();
        return isComputed;
    }

    String checkContributionAbility(String chainTaskId) {
        Optional<ReplicateStatusCause> oCannotContributeStatusCause =
                contributionService.getCannotContributeStatusCause(chainTaskId);

        if (!oCannotContributeStatusCause.isPresent()) return "";

        String errorMessage = "Cannot contribute";
        log.error(errorMessage + " [chainTaskId:{}, cause:{}]", chainTaskId, oCannotContributeStatusCause.get());
        customFeignClient.updateReplicateStatus(chainTaskId, CANT_CONTRIBUTE, oCannotContributeStatusCause.get());
        return errorMessage;
    }

    String checkIfAppImageExists(String chainTaskId, String imageUri) {
        if (customDockerClient.isImagePulled(imageUri)) return "";

        String errorMessage = "Application image not found, URI:" + imageUri;
        log.error(errorMessage + " [chainTaskId:{}]", chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTE_FAILED);
        return errorMessage;
    }

    String getTaskDeterminismHash(String chainTaskId) {
        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);

        if (isTeeTask && determinismHash.isEmpty()) {
            log.error("Cannot continue, couldn't get TEE determinism hash [chainTaskId:{}]", chainTaskId);
            //TODO change that to CAUSE
            customFeignClient.updateReplicateStatus(chainTaskId,
                    CANT_CONTRIBUTE, TEE_EXECUTION_NOT_VERIFIED);
            return "";
        }

        if (determinismHash.isEmpty()) {
            log.error("Cannot continue, couldn't get determinism hash [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId,
                    CANT_CONTRIBUTE, DETERMINISM_HASH_NOT_FOUND);
            return "";
        }

        return determinismHash;
    }

    Optional<Signature> getVerifiedEnclaveSignature(String chainTaskId, String signerAddress) {
        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);
        if (!isTeeTask) return Optional.of(SignatureUtils.emptySignature());

        Optional<SconeEnclaveSignatureFile> oSconeEnclaveSignatureFile =
                resultService.readSconeEnclaveSignatureFile(chainTaskId);

        if (!oSconeEnclaveSignatureFile.isPresent()) {
            log.error("Error reading and parsing enclaveSig.iexec file [chainTaskId:{}]", chainTaskId);
            log.error("Cannot contribute, TEE execution not verified [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_CONTRIBUTE, TEE_EXECUTION_NOT_VERIFIED);
            return Optional.empty();
        }

        SconeEnclaveSignatureFile sconeEnclaveSignatureFile = oSconeEnclaveSignatureFile.get();
        Signature enclaveSignature = new Signature(sconeEnclaveSignatureFile.getSignature());
        String resultHash = sconeEnclaveSignatureFile.getResultHash();
        String resultSeal = sconeEnclaveSignatureFile.getResultSalt();

        boolean isValid = sconeTeeService.isEnclaveSignatureValid(resultHash, resultSeal,
                enclaveSignature, signerAddress);

        if (!isValid) {
            log.error("Scone enclave signature is not valid [chainTaskId:{}]", chainTaskId);
            log.error("Cannot contribute, TEE execution not verified [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_CONTRIBUTE, TEE_EXECUTION_NOT_VERIFIED);
            return Optional.empty();
        }

        return Optional.of(enclaveSignature);
    }

    boolean checkGasBalance(String chainTaskId) {
        if (iexecHubService.hasEnoughGas()) return true;

        customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
        String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                workerConfigurationService.getWorkerWalletAddress());
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    boolean isValidChainReceipt(String chainTaskId, Optional<ChainReceipt> oChainReceipt) {
        if (!oChainReceipt.isPresent()) {
            log.warn("The chain receipt is empty, nothing will be sent to the core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (oChainReceipt.get().getBlockNumber() == 0) {
            log.warn("The blockNumber of the receipt is equal to 0, status will not be "
                    + "updated in the core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    public boolean contribute(String chainTaskId) {
        ContributionAuthorization contributionAuthorization = contributionAuthorizations.get(chainTaskId);

        if (contributionAuthorization == null){
            log.error("Contribution auth missing for compute [chainTaskId:{}]", chainTaskId);
            return false;
        }

        String enclaveChallenge = contributionAuthorization.getEnclaveChallenge();
        log.info("Trying to contribute [chainTaskId:{}]", chainTaskId);

        String determinismHash = getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) return false;

        Optional<Signature> oEnclaveSignature = getVerifiedEnclaveSignature(chainTaskId, enclaveChallenge);
        if (!oEnclaveSignature.isPresent()) return false;

        Signature enclaveSignature = oEnclaveSignature.get();

        String contributionAbilityError = checkContributionAbility(chainTaskId);
        if (!contributionAbilityError.isEmpty()) return false;

        //customFeignClient.updateReplicateStatus(chainTaskId, ReplicateStatus.CAN_CONTRIBUTE);

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) System.exit(0);

        Optional<ChainReceipt> oChainReceipt =
                contributionService.contribute(contributionAuthorization, determinismHash, enclaveSignature);

        boolean isValidChainReceipt = isValidChainReceipt(chainTaskId, oChainReceipt);
        if (isValidChainReceipt){
            return true;
        }
        return false;
    }

    public boolean reveal(String chainTaskId, TaskNotificationExtra extra) {

        if (extra == null || extra.getBlockNumber() == 0){
            log.error("ConsensusBlock missing for reveal [chainTaskId:{}]", chainTaskId);
            return false;
        }
        long consensusBlock = extra.getBlockNumber();

        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        String determinismHash = getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) return false;

        boolean isBlockReached = revealService.isConsensusBlockReached(chainTaskId, consensusBlock);
        boolean canReveal = revealService.canReveal(chainTaskId, determinismHash);

        if (!isBlockReached || !canReveal) {
            log.error("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_REVEAL);
            return false;
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) System.exit(0);

        Optional<ChainReceipt> oChainReceipt = revealService.reveal(chainTaskId, determinismHash);

        boolean isValidChainReceipt = isValidChainReceipt(chainTaskId, oChainReceipt);
        if (isValidChainReceipt){
            return true;
        }

        return false;
    }

    public void abortConsensusReached(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONSENSUS_REACHED);
    }

    public boolean abort(String chainTaskId) {
        return resultService.removeResult(chainTaskId);
    }

    public ReplicateDetails uploadResult(String chainTaskId) {

        boolean isResultEncryptionNeeded = resultService.isResultEncryptionNeeded(chainTaskId);
        boolean isResultEncrypted = false;

        if (isResultEncryptionNeeded) {
            isResultEncrypted = resultService.encryptResult(chainTaskId);
        }

        if (isResultEncryptionNeeded && !isResultEncrypted) {
            log.error("Failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return null;
        }

        String resultLink = resultService.uploadResult(chainTaskId);

        if (resultLink.isEmpty()) {
            log.error("ResultLink missing (aborting) [chainTaskId:{}]", chainTaskId);
            return null;
        }

        String callbackData = resultService.getCallbackDataFromFile(chainTaskId);

        log.info("Uploaded result with details [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();

        return details;
    }

    public boolean completeTask(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        return true;
    }


        /*
    public void computeOrContribute(ContributionAuthorization contributionAuth) {

        String chainTaskId = contributionAuth.getChainTaskId();

        boolean isResultAvailable = resultService.isResultAvailable(chainTaskId);

        if (isResultAvailable) {
            log.info("Result found, will restart task from CONTRIBUTING [chainTaskId:{}]", chainTaskId);
            contribute(contributionAuth);
            return;
        }

        addReplicate(contributionAuth);
        */
}