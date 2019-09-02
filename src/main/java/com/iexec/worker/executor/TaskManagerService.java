package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static com.iexec.common.replicate.ReplicateStatusUpdate.*;


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
            log.info("Some tasks are using CPU [tasksUsingCpu:{}, maxTasksUsingCpu:{}]", tasksUsingCpu.size(), maxNbExecutions);
        }
        return tasksUsingCpu.size() <= maxNbExecutions;
    }

    private void setTaskUsingCpu(String chainTaskId) {
        if (tasksUsingCpu.contains(chainTaskId)) {
            return;
        }

        tasksUsingCpu.add(chainTaskId);
        log.info("Set task using CPU [tasksUsingCpu:{}]", tasksUsingCpu.size());
    }

    private void unsetTaskUsingCpu(String chainTaskId) {
        tasksUsingCpu.remove(chainTaskId);
        log.info("Unset task using CPU [tasksUsingCpu:{}]", tasksUsingCpu.size());
    }

    ReplicateStatusUpdate start(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot start, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return workerRequest(START_FAILED, oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot start, task description not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(START_FAILED, TASK_DESCRIPTION_NOT_FOUND);
        }

        if (taskDescription.isTeeTask() && !sconeTeeService.isTeeEnabled()) {
            log.error("Cannot start, TEE not supported [chainTaskId:{}]", chainTaskId);
            return workerRequest(START_FAILED, TEE_NOT_SUPPORTED);
        }

        return workerRequest(STARTED);
    }

    ReplicateStatusUpdate downloadApp(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot download app, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return workerRequest(APP_DOWNLOAD_FAILED, oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot download app, task description not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(APP_DOWNLOAD_FAILED, TASK_DESCRIPTION_NOT_FOUND);
        }

        if (!computationService.downloadApp(chainTaskId, taskDescription)) {
            log.error("Failed to download app [chainTaskId:{}]", chainTaskId);
            return workerRequest(APP_DOWNLOAD_FAILED);
        }
        
        return workerRequest(APP_DOWNLOADED);
    }

    ReplicateStatusUpdate downloadData(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot download data, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return workerRequest(DATA_DOWNLOAD_FAILED, oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot download data, task description not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(DATA_DOWNLOAD_FAILED, TASK_DESCRIPTION_NOT_FOUND);
        }
        
        String datasetUri = taskDescription.getDatasetUri();
        List<String> inputFiles = taskDescription.getInputFiles();
        if (datasetUri == null || !dataService.downloadFile(chainTaskId, datasetUri)) {
            log.error("Failed to download data [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, taskDescription.getDatasetUri());
            return workerRequest(DATA_DOWNLOAD_FAILED);
        }

        if (inputFiles != null && !dataService.downloadFiles(chainTaskId, inputFiles)){
            log.error("Failed to download input files [chainTaskId:{}, inputFiles:{}]",
                    chainTaskId, taskDescription.getInputFiles());
            return workerRequest(DATA_DOWNLOAD_FAILED);
        }

        return workerRequest(DATA_DOWNLOADED);
    }

    ReplicateStatusUpdate compute(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot compute, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return workerRequest(COMPUTE_FAILED, oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot compute, task description not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(COMPUTE_FAILED, TASK_DESCRIPTION_NOT_FOUND);
        }

        if (!computationService.isAppDownloaded(taskDescription.getAppUri())) {
            log.error("Cannot compute, app not found locally [chainTaskId:{}]", chainTaskId);
            return workerRequest(COMPUTE_FAILED, APP_NOT_FOUND_LOCALLY);
        }

        boolean isComputed;
        ContributionAuthorization contributionAuthorization =
                contributionService.getContributionAuthorization(chainTaskId);

        if (taskDescription.isTeeTask()) {
            isComputed = computationService.runTeeComputation(taskDescription, contributionAuthorization);
        } else {
            isComputed = computationService.runNonTeeComputation(taskDescription, contributionAuthorization);
        }

        if (!isComputed) {
            log.error("Failed to compute [chainTaskId:{}]", chainTaskId);
            return workerRequest(COMPUTE_FAILED);
        }

        return workerRequest(COMPUTED);
    }

    ReplicateStatusUpdate contribute(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot contribute, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return workerRequest(CONTRIBUTE_FAILED, oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot contribute, task description not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(CONTRIBUTE_FAILED, TASK_DESCRIPTION_NOT_FOUND);
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) {
            log.error("Cannot contribute, no enough gas [chainTaskId:{}]", chainTaskId);
            return workerRequest(CONTRIBUTE_FAILED, OUT_OF_GAS);
            // System.exit(0);
        }

        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) {
            log.error("Cannot contribute, determinism hash not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(CONTRIBUTE_FAILED, DETERMINISM_HASH_NOT_FOUND);
        }

        ContributionAuthorization contributionAuthorization =
                contributionService.getContributionAuthorization(chainTaskId);

        String enclaveChallenge = contributionAuthorization.getEnclaveChallenge();
        Optional<Signature> enclaveSignature = getVerifiedEnclaveSignature(chainTaskId, enclaveChallenge);
        if (enclaveSignature.isEmpty()) {//could be 0x0
            log.error("Cannot contribute enclave signature not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(CONTRIBUTE_FAILED, ENCLAVE_SIGNATURE_NOT_FOUND);
        }

        Optional<ChainReceipt> oChainReceipt =
                contributionService.contribute(contributionAuthorization, determinismHash, enclaveSignature.get());

        if(!isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return workerRequest(CONTRIBUTE_FAILED, CHAIN_RECEIPT_NOT_VALID);
        }

        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .chainReceipt(oChainReceipt.get())
                .build();

        return workerRequest(CONTRIBUTED, details);
    }

    ReplicateStatusUpdate reveal(String chainTaskId, TaskNotificationExtra extra) {
        unsetTaskUsingCpu(chainTaskId);

        String determinismHash = resultService.getTaskDeterminismHash(chainTaskId);
        if (determinismHash.isEmpty()) {
            log.error("Cannot reveal, determinism hash not found [chainTaskId:{}]", chainTaskId);
            return workerRequest(REVEAL_FAILED, DETERMINISM_HASH_NOT_FOUND);
        }

        if (extra == null || extra.getBlockNumber() == 0) {
            log.error("Cannot reveal, missing consensus block [chainTaskId:{}]", chainTaskId);
            return workerRequest(REVEAL_FAILED, CONSENSUS_BLOCK_MISSING);
        }

        long consensusBlock = extra.getBlockNumber();
        boolean isBlockReached = revealService.isConsensusBlockReached(chainTaskId, consensusBlock);
        if (!isBlockReached) {
            log.error("Cannot reveal, consensus block not reached [chainTaskId:{}]", chainTaskId);
            return workerRequest(REVEAL_FAILED, BLOCK_NOT_REACHED);
        }

        boolean canReveal = revealService.canReveal(chainTaskId, determinismHash);
        if (!canReveal) {
            log.error("Cannot reveal, one or more conditions are not satisfied [chainTaskId:{}]", chainTaskId);
            return workerRequest(REVEAL_FAILED, CANNOT_REVEAL);
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) {
            log.error("Cannot reveal, no enough gas [chainTaskId:{}]", chainTaskId);
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt = revealService.reveal(chainTaskId, determinismHash);
        if(!isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return workerRequest(REVEAL_FAILED, CHAIN_RECEIPT_NOT_VALID);
        }

        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .chainReceipt(oChainReceipt.get())
                .build();

        return workerRequest(REVEALED, details);
    }

    ReplicateStatusUpdate uploadResult(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);

        boolean isResultEncryptionNeeded = resultService.isResultEncryptionNeeded(chainTaskId);
        boolean isResultEncrypted = false;

        if (isResultEncryptionNeeded) {
            isResultEncrypted = resultService.encryptResult(chainTaskId);
        }

        if (isResultEncryptionNeeded && !isResultEncrypted) {
            log.error("Cannot upload, failed to encrypt result [chainTaskId:{}]", chainTaskId);
            return workerRequest(RESULT_UPLOAD_FAILED, RESULT_ENCRYPTION_FAILED);
        }

        String resultLink = resultService.uploadResult(chainTaskId);
        if (resultLink.isEmpty()) {
            log.error("Cannot upload, resultLink missing [chainTaskId:{}]", chainTaskId);
            return workerRequest(RESULT_UPLOAD_FAILED, RESULT_LINK_MISSING);
        }

        String callbackData = resultService.getCallbackDataFromFile(chainTaskId);

        log.info("Result uploaded [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();

        return workerRequest(RESULT_UPLOADED, details);
    }

    ReplicateStatusUpdate complete(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        if (!resultService.removeResult(chainTaskId)) {
            return workerRequest(COMPLETE_FAILED);
        }

        return workerRequest(COMPLETED);
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

        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.workerRequest(ABORTED, OUT_OF_GAS);
        customCoreFeignClient.updateReplicateStatus(chainTaskId, statusUpdate);
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