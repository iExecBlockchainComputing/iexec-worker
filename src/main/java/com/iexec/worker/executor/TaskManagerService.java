package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeEnclaveSignatureFile;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.replicate.ReplicateStatus.OUT_OF_GAS;


@Slf4j
@Service
public class TaskManagerService {

    private final int maxNbExecutions;
    private DataService dataService;
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomCoreFeignClient customCoreFeignClient;
    private WorkerConfigurationService workerConfigurationService;
    private SconeTeeService sconeTeeService;
    private IexecHubService iexecHubService;
    private RevealService revealService;
    private ComputationService computationService;
    private Set<String> tasksUsingCpu;

    public TaskManagerService(DataService dataService,
                              ResultService resultService,
                              ContributionService contributionService,
                              CustomCoreFeignClient customCoreFeignClient,
                              WorkerConfigurationService workerConfigurationService,
                              SconeTeeService sconeTeeService,
                              IexecHubService iexecHubService,
                              ComputationService computationService,
                              RevealService revealService) {
        this.dataService = dataService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.workerConfigurationService = workerConfigurationService;
        this.sconeTeeService = sconeTeeService;
        this.iexecHubService = iexecHubService;
        this.computationService = computationService;
        this.revealService = revealService;

        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        tasksUsingCpu = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }
    
    public boolean canAcceptMoreReplicates() {
        if (tasksUsingCpu.size() > 0) {
            log.info("Some task are using CPU [tasksUsingCpu:{}, maxTasksUsingCpu:{}]", tasksUsingCpu.size(), maxNbExecutions);
        }
        return tasksUsingCpu.size() <= maxNbExecutions;
    }

    private void setTaskUsingCpu(String chainTaskId) {
        tasksUsingCpu.add(chainTaskId);
        log.info("Set task using CPU [tasksUsingCpu:{}]", tasksUsingCpu.size());
    }

    private void unsetTaskUsingCpu(String chainTaskId) {
        tasksUsingCpu.remove(chainTaskId);
        log.info("Unset task using CPU [tasksUsingCpu:{}]", tasksUsingCpu.size());
    }

    boolean start(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        if (!contributionService.isChainTaskInitialized(chainTaskId)) {
            log.error("Cant start (task not initialized) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant start (taskDescription missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (contributionService.getCannotContributeStatusCause(chainTaskId).isPresent()) {
            log.error("Cant start (cant contribute) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (taskDescription.isTeeTask() && !sconeTeeService.isTeeEnabled()) {
            log.error("Cant start (tee task but not supported) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    boolean downloadApp(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant download app (taskDescription missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (contributionService.getCannotContributeStatusCause(chainTaskId).isPresent()) {
            log.error("Cant download app (cant contribute) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (!computationService.downloadApp(chainTaskId, taskDescription)) {
            log.error("Cant download app (download failed) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    boolean downloadData(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant download data (taskDescription missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (contributionService.getCannotContributeStatusCause(chainTaskId).isPresent()) {
            log.error("Cant download data (cant contribute) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (taskDescription.getDatasetUri() == null || !dataService.downloadFile(chainTaskId, taskDescription.getDatasetUri())) {
            log.error("Cant download data (download iexec dataset failed) [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, taskDescription.getDatasetUri());
            return false;
        }

        if (taskDescription.getInputFiles() != null && !dataService.downloadFiles(chainTaskId, taskDescription.getInputFiles())){
            log.error("Cant download data (download input files failed) [chainTaskId:{}, inputFiles:{}]",
                    chainTaskId, taskDescription.getInputFiles());
            return false;
        }

        return true;
    }

    boolean compute(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant compute (taskDescription missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (contributionService.getCannotContributeStatusCause(chainTaskId).isPresent()) {
            log.error("Cant compute (cant contribute) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (!computationService.isAppDownloaded(taskDescription.getAppUri())) {
            log.error("Cant compute (app not downloaded) [chainTaskId:{}]", chainTaskId);
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

        if (!isComputed) {
            log.error("Cant compute (compute failed) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    boolean contribute(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cant contribute (taskDescription missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (contributionService.getCannotContributeStatusCause(chainTaskId).isPresent()) {
            log.error("Cant contribute (cant contribute) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) {
            log.error("Cant contribute (no more gas) [chainTaskId:{}]", chainTaskId);
            System.exit(0);
        }

        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) {
            log.error("Cant contribute (determinism hash missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        ContributionAuthorization contributionAuthorization = contributionService.getContributionAuthorization(chainTaskId);
        String enclaveChallenge = contributionAuthorization.getEnclaveChallenge();
        Optional<Signature> enclaveSignature = getVerifiedEnclaveSignature(chainTaskId, enclaveChallenge);
        if (enclaveSignature.isEmpty()) {//could be 0x0
            log.error("Cant contribute (enclave signature missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        Optional<ChainReceipt> chainReceipt =
                contributionService.contribute(contributionAuthorization, determinismHash, enclaveSignature.get());

        return isValidChainReceipt(chainTaskId, chainReceipt);
    }

    boolean reveal(String chainTaskId, TaskNotificationExtra extra) {
        unsetTaskUsingCpu(chainTaskId);

        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) {
            log.error("Cant reveal (determinism hash missing) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (extra == null || extra.getBlockNumber() == 0) {
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
        if (!hasEnoughGas) {
            log.error("Cant reveal (no more gas) [chainTaskId:{}]", chainTaskId);
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt = revealService.reveal(chainTaskId, determinismHash);

        return isValidChainReceipt(chainTaskId, oChainReceipt);
    }

    ReplicateDetails uploadResult(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);

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

        return ReplicateDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();
    }

    boolean complete(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        resultService.removeResult(chainTaskId);
        return true;
    }

    boolean abort(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        return resultService.removeResult(chainTaskId);
    }


    //TODO Move that to result service
    Optional<Signature> getVerifiedEnclaveSignature(String chainTaskId, String signerAddress) {
        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);
        if (!isTeeTask) {
            return Optional.of(SignatureUtils.emptySignature());
        }

        Optional<SconeEnclaveSignatureFile> oSconeEnclaveSignatureFile =
                resultService.readSconeEnclaveSignatureFile(chainTaskId);
        if (oSconeEnclaveSignatureFile.isEmpty()) {
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
        if (iexecHubService.hasEnoughGas()) {
            return true;
        }

        customCoreFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
        String noEnoughGas = String.format("Out of gas! please refill your wallet [walletAddress:%s]",
                workerConfigurationService.getWorkerWalletAddress());
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    //TODO Move that to contribute & reveal services
    boolean isValidChainReceipt(String chainTaskId, Optional<ChainReceipt> oChainReceipt) {
        if (oChainReceipt.isEmpty()) {
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