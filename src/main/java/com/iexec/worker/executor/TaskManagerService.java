/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.contribution.Contribution;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.compute.ComputeService;
import com.iexec.worker.compute.Compute;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.replicate.ReplicateStatusCause.*;


@Slf4j
@Service
public class TaskManagerService {

    private final int maxNbExecutions;
    private DataService dataService;
    private ResultService resultService;
    private ContributionService contributionService;
    private WorkerConfigurationService workerConfigurationService;
    private SconeTeeService sconeTeeService;
    private IexecHubService iexecHubService;
    private RevealService revealService;
    private ComputeService computeService;
    private Set<String> tasksUsingCpu;

    public TaskManagerService(DataService dataService,
                              ResultService resultService,
                              ContributionService contributionService,
                              WorkerConfigurationService workerConfigurationService,
                              SconeTeeService sconeTeeService,
                              IexecHubService iexecHubService,
                              ComputeService computationService,
                              RevealService revealService) {
        this.dataService = dataService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.workerConfigurationService = workerConfigurationService;
        this.sconeTeeService = sconeTeeService;
        this.iexecHubService = iexecHubService;
        this.computeService = computationService;
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

    ReplicateActionResponse start(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot start [chainTaskId:{}, error:{}]", chainTaskId, oErrorStatus.get());
            return ReplicateActionResponse.failure(oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot start, task description not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND);
        }

        if (taskDescription.isTeeTask() && !sconeTeeService.isTeeEnabled()) {
            log.error("Cannot start, TEE not supported [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TEE_NOT_SUPPORTED);
        }

        return ReplicateActionResponse.success();
    }

    ReplicateActionResponse downloadApp(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot download app, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return ReplicateActionResponse.failure(oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot download app, task description not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND);
        }

        if (!computeService.downloadApp(chainTaskId, taskDescription)) {
            log.error("Failed to download app [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure();
        }

        return ReplicateActionResponse.success();
    }

    ReplicateActionResponse downloadData(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot download data, {} [chainTaskId:{}]", oErrorStatus.get(), chainTaskId);
            return ReplicateActionResponse.failure(oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot download data, task description not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND);
        }

        String datasetUri = taskDescription.getDatasetUri();
        List<String> inputFiles = taskDescription.getInputFiles();
        if (!datasetUri.isEmpty()) {
            boolean isDatasetReady = dataService.downloadFile(chainTaskId, datasetUri);
            String errorMessage = "Failed to download dataset";
            if (taskDescription.isTeeTask()) {
                isDatasetReady = dataService.unzipDownloadedTeeDataset(chainTaskId, datasetUri);
                errorMessage = "Failed to unzip downloaded Tee dataset";
            }
            if (!isDatasetReady) {
                log.error(errorMessage + " [chainTaskId:{}, datasetUri:{}]", chainTaskId, taskDescription.getDatasetUri());
                return ReplicateActionResponse.failure();
            }
        }

        if (inputFiles != null && !dataService.downloadFiles(chainTaskId, inputFiles)) {
            log.error("Failed to download input files [chainTaskId:{}, inputFiles:{}]",
                    chainTaskId, taskDescription.getInputFiles());
            return ReplicateActionResponse.failure();
        }

        return ReplicateActionResponse.success();
    }

    ReplicateActionResponse compute(String chainTaskId) {
        setTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot compute [chainTaskId:{}, error:{}]", chainTaskId, oErrorStatus.get());
            return ReplicateActionResponse.failure(oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot compute, task description not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND);
        }

        if (!computeService.isAppDownloaded(taskDescription.getAppUri())) {
            log.error("Cannot compute, app not found locally [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(APP_NOT_FOUND_LOCALLY);
        }

        WorkerpoolAuthorization workerpoolAuthorization =
                contributionService.getWorkerpoolAuthorization(chainTaskId);

        Compute compute = Compute.builder().chainTaskId(chainTaskId).build();
        computeService.runPreCompute(compute, taskDescription, workerpoolAuthorization);
        if (!compute.isPreComputed()) {
            log.error("Failed to pre-compute [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(PRE_COMPUTE_FAILED);
        }
        computeService.runCompute(compute, taskDescription);
        if (!compute.isComputed()) {
            log.error("Failed to compute [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failureWithStdout(compute.getStdout());
        }
        computeService.runPostCompute(compute, taskDescription);
        if (!compute.isPostComputed()) {
            log.error("Failed to post-compute [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failureWithStdout(POST_COMPUTE_FAILED, compute.getStdout());
        }
        resultService.saveResultInfo(chainTaskId, taskDescription);
        return ReplicateActionResponse.successWithStdout(compute.getStdout());
    }

    ReplicateActionResponse contribute(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);

        Optional<ReplicateStatusCause> oErrorStatus = contributionService.getCannotContributeStatusCause(chainTaskId);
        if (oErrorStatus.isPresent()) {
            log.error("Cannot contribute [chainTaskId:{}, error:{}]", chainTaskId, oErrorStatus.get());
            return ReplicateActionResponse.failure(oErrorStatus.get());
        }

        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            log.error("Cannot contribute, task description not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND);
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) {
            log.error("Cannot contribute, no enough gas [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(OUT_OF_GAS);
            // System.exit(0);
        }

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
        if (computedFile == null) {
            log.error("Cannot contribute, getComputedFile [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        Contribution contribution = contributionService.getContribution(computedFile);
        if (contribution == null) {
            log.error("Failed to getContribution [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(ENCLAVE_SIGNATURE_NOT_FOUND);//TODO update status
        }

        Optional<ChainReceipt> oChainReceipt = contributionService.contribute(contribution);

        if (!isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID);
        }

        return ReplicateActionResponse.success(oChainReceipt.get());
    }

    ReplicateActionResponse reveal(String chainTaskId, TaskNotificationExtra extra) {
        unsetTaskUsingCpu(chainTaskId);

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
        String resultDigest = computedFile != null ? computedFile.getResultDigest() : "";

        if (resultDigest.isEmpty()) {
            log.error("Cannot reveal, resultDigest not found [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        if (extra == null || extra.getBlockNumber() == 0) {
            log.error("Cannot reveal, missing consensus block [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(CONSENSUS_BLOCK_MISSING);
        }

        long consensusBlock = extra.getBlockNumber();
        boolean isBlockReached = revealService.isConsensusBlockReached(chainTaskId, consensusBlock);
        if (!isBlockReached) {
            log.error("Cannot reveal, consensus block not reached [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(BLOCK_NOT_REACHED);
        }

        boolean canReveal = revealService.repeatCanReveal(chainTaskId, resultDigest);

        if (!canReveal) {
            log.error("Cannot reveal, one or more conditions are not satisfied [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(CANNOT_REVEAL);
        }

        boolean hasEnoughGas = checkGasBalance(chainTaskId);
        if (!hasEnoughGas) {
            log.error("Cannot reveal, no enough gas [chainTaskId:{}]", chainTaskId);
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt = revealService.reveal(chainTaskId, resultDigest);
        if (!isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID);
        }

        return ReplicateActionResponse.success(oChainReceipt.get());
    }

    ReplicateActionResponse uploadResult(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        String resultLink = resultService.uploadResultAndGetLink(chainTaskId);
        if (resultLink.isEmpty()) {
            log.error("Cannot upload, resultLink missing [chainTaskId:{}]", chainTaskId);
            return ReplicateActionResponse.failure(RESULT_LINK_MISSING);
        }

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
        String callbackData = computedFile != null ? computedFile.getCallbackData() : "";

        log.info("Result uploaded [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        return ReplicateActionResponse.success(resultLink, callbackData);
    }

    ReplicateActionResponse complete(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        if (!resultService.removeResult(chainTaskId)) {
            return ReplicateActionResponse.failure();
        }

        return ReplicateActionResponse.success();
    }

    boolean abort(String chainTaskId) {
        unsetTaskUsingCpu(chainTaskId);
        return resultService.removeResult(chainTaskId);
    }

    boolean checkGasBalance(String chainTaskId) {
        if (iexecHubService.hasEnoughGas()) {
            return true;
        }

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