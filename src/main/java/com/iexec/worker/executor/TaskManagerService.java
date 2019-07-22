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
    private RevealService revealService;
    private ComputationService computationService;

    public TaskManagerService(DatasetService datasetService,
                              ResultService resultService,
                              ContributionService contributionService,
                              CustomFeignClient customFeignClient,
                              WorkerConfigurationService workerConfigurationService,
                              SconeTeeService sconeTeeService,
                              IexecHubService iexecHubService,
                              ComputationService computationService,
                              RevealService revealService) {
        this.datasetService = datasetService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customFeignClient = customFeignClient;
        this.workerConfigurationService = workerConfigurationService;
        this.sconeTeeService = sconeTeeService;
        this.iexecHubService = iexecHubService;
        this.computationService = computationService;
        this.revealService = revealService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);

    }

    public boolean canAcceptMoreReplicates() {
        //return executor.getActiveCount() < maxNbExecutions;
        return true;
    }


    public boolean start(String chainTaskId){
        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Cant start (task not initialized) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant start (taskDescription missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()){
            log.error("Cant start (cant contribute) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (taskDescription.isTeeTask() && !workerConfigurationService.isTeeEnabled()) {
            log.error("Cant start (tee task but not supported) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }



    boolean downloadApp(String chainTaskId) {
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant download app (taskDescription missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()){
            log.error("Cant download app (cant contribute) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!computationService.downloadApp(chainTaskId, taskDescription)) {
            log.error("Cant download app (download failed) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        return true;
    }

    boolean downloadData(String chainTaskId) {
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant download data (taskDescription missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()){
            log.error("Cant download data (cant contribute) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!datasetService.downloadDataset(chainTaskId, taskDescription.getDatasetUri())){
            log.error("Cant download data (download failed) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        return true;
    }

    boolean compute(String chainTaskId) {
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant compute (taskDescription missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()){
            log.error("Cant compute (cant contribute) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!computationService.isAppDownloaded(taskDescription.getAppUri())){
            log.error("Cant compute (app not downloaded) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        //TODO add isDataSetDownloaded ?

        boolean isComputed;
        ContributionAuthorization contributionAuthorization = contributionService.getContributionAuthorization(chainTaskId);
        if (taskDescription.isTeeTask()) {
            isComputed = computationService.runTeeComputation(taskDescription, contributionAuthorization);
        } else {
            isComputed = computationService.runNonTeeComputation(taskDescription, contributionAuthorization);
        }

        if (!isComputed){
            log.error("Cant compute (compute failed) [chainTaskId:{}]",chainTaskId);
        }

        return isComputed;
    }

    public boolean contribute(String chainTaskId) {
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant contribute (taskDescription missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (!contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()){
            log.error("Cant contribute (cant contribute) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas){
            log.error("Cant contribute (no more gas) [chainTaskId:{}]",chainTaskId);
            System.exit(0);
        }

        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()){
            log.error("Cant contribute (determinism hash missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        ContributionAuthorization contributionAuthorization = contributionService.getContributionAuthorization(chainTaskId);
        String enclaveChallenge = contributionAuthorization.getEnclaveChallenge();
        Optional<Signature> enclaveSignature = getVerifiedEnclaveSignature(chainTaskId, enclaveChallenge);
        if (enclaveSignature.isEmpty()){//could be 0x0
            log.error("Cant contribute (enclave signature missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        Optional<ChainReceipt> chainReceipt =
                contributionService.contribute(contributionAuthorization, determinismHash, enclaveSignature.get());

        return isValidChainReceipt(chainTaskId, chainReceipt);
    }

    public boolean reveal(String chainTaskId, TaskNotificationExtra extra) {
        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()){
            log.error("Cant reveal (determinism hash missing) [chainTaskId:{}]",chainTaskId);
            return false;
        }

        if (extra == null || extra.getBlockNumber() == 0){
            log.error("Cant reveal (consensusBlock missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }
        long consensusBlock = extra.getBlockNumber();
        boolean isBlockReached = revealService.isConsensusBlockReached(chainTaskId, consensusBlock);
        if (!isBlockReached) {
            log.error("Cant reveal (consensus block not reached)[chainTaskId:{}]", chainTaskId);
            return false;
        }

        boolean canReveal = revealService.canReveal(chainTaskId, determinismHash);
        if (!canReveal) {
            log.error("Cant reveal (cant reveal) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas){
            log.error("Cant reveal (no more gas) [chainTaskId:{}]",chainTaskId);
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt = revealService.reveal(chainTaskId, determinismHash);

        return  isValidChainReceipt(chainTaskId, oChainReceipt);
    }

    public ReplicateDetails uploadResult(String chainTaskId) {
        boolean isResultEncryptionNeeded = resultService.isResultEncryptionNeeded(chainTaskId);
        boolean isResultEncrypted = false;

        if (isResultEncryptionNeeded) {
            isResultEncrypted = resultService.encryptResult(chainTaskId);
        }

        if (isResultEncryptionNeeded && !isResultEncrypted) {
            log.error("Cant upload (encrypt result failed) [chainTaskId:{}]", chainTaskId);
            return null;
        }

        String resultLink = resultService.uploadResult(chainTaskId);
        if (resultLink.isEmpty()) {
            log.error("Cant upload (resultLink missing) [chainTaskId:{}]", chainTaskId);
            return null;
        }

        String callbackData = resultService.getCallbackDataFromFile(chainTaskId);

        log.info("Result uploaded [chainTaskId:{}, resultLink:{}, callbackData:{}]", chainTaskId, resultLink, callbackData);

        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();

        return details;
    }

    public boolean complete(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        return true;
    }

    public boolean abort(String chainTaskId) {
        return resultService.removeResult(chainTaskId);
    }


    //TODO Move that to result service
    Optional<Signature> getVerifiedEnclaveSignature(String chainTaskId, String signerAddress) {
        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);
        if (!isTeeTask){
            return Optional.of(SignatureUtils.emptySignature());
        }

        Optional<SconeEnclaveSignatureFile> oSconeEnclaveSignatureFile =
                resultService.readSconeEnclaveSignatureFile(chainTaskId);
        if (!oSconeEnclaveSignatureFile.isPresent()) {
            log.error("Error reading and parsing enclaveSig.iexec file [chainTaskId:{}]", chainTaskId);
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
            return Optional.empty();
        }

        return Optional.of(enclaveSignature);
    }

    boolean checkGasBalance(String chainTaskId) {
        if (iexecHubService.hasEnoughGas()){
            return true;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
        String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                workerConfigurationService.getWorkerWalletAddress());
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    //TODO Move that to contribute & reveal services
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

}