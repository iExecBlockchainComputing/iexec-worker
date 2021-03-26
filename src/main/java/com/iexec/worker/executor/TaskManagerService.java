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
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.APP_DOWNLOAD_FAILED;
import static com.iexec.common.replicate.ReplicateStatus.DATA_DOWNLOAD_FAILED;
import static com.iexec.common.replicate.ReplicateStatusCause.*;


@Slf4j
@Service
public class TaskManagerService {

    private final WorkerConfigurationService workerConfigurationService;
    private final IexecHubService iexecHubService;
    private final ContributionService contributionService;
    private final RevealService revealService;
    private final ComputeManagerService computeManagerService;
    private final SconeTeeService sconeTeeService;
    private final DataService dataService;
    private final ResultService resultService;

    public TaskManagerService(
            WorkerConfigurationService workerConfigurationService,
            IexecHubService iexecHubService,
            ContributionService contributionService,
            RevealService revealService,
            ComputeManagerService computeManagerService,
            SconeTeeService sconeTeeService,
            DataService dataService,
            ResultService resultService
    ) {
        this.workerConfigurationService = workerConfigurationService;
        this.iexecHubService = iexecHubService;
        this.contributionService = contributionService;
        this.revealService = revealService;
        this.computeManagerService = computeManagerService;
        this.sconeTeeService = sconeTeeService;
        this.dataService = dataService;
        this.resultService = resultService;
    }

    ReplicateActionResponse start(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "start";
        if (oErrorStatus.isPresent()) {
            return getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        TaskDescription taskDescription =
                iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            return getFailureResponseAndPrintError(TASK_DESCRIPTION_NOT_FOUND,
                    context, chainTaskId);
        }

        if (taskDescription.isTeeTask() && !sconeTeeService.isTeeEnabled()) {
            return getFailureResponseAndPrintError(TEE_NOT_SUPPORTED,
                    context, chainTaskId);
        }

        return ReplicateActionResponse.success();
    }

    ReplicateActionResponse downloadApp(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "download app";
        if (oErrorStatus.isPresent()) {
            return getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        TaskDescription taskDescription =
                iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            return getFailureResponseAndPrintError(TASK_DESCRIPTION_NOT_FOUND,
                    context, chainTaskId);
        }

        if (computeManagerService.downloadApp(taskDescription)) {
            return ReplicateActionResponse.success();
        }
        return triggerPostComputeHookOnError(chainTaskId, context, taskDescription,
                APP_DOWNLOAD_FAILED, APP_IMAGE_DOWNLOAD_FAILED);
    }

    ReplicateActionResponse downloadData(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "download data";
        if (oErrorStatus.isPresent()) {
            return getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        TaskDescription taskDescription =
                iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            return getFailureResponseAndPrintError(TASK_DESCRIPTION_NOT_FOUND,
                    context, chainTaskId);
        }

        String datasetUri = taskDescription.getDatasetUri();
        if (!datasetUri.isEmpty()) {
            boolean isDatasetReady = dataService.downloadFile(chainTaskId,
                    datasetUri);
            if (taskDescription.isTeeTask()) {
                isDatasetReady =
                        dataService.unzipDownloadedTeeDataset(chainTaskId,
                                datasetUri);
                logError("unzip dataset error", context, chainTaskId);
            }
            if (!isDatasetReady) {
                return triggerPostComputeHookOnError(chainTaskId, context,
                        taskDescription, DATA_DOWNLOAD_FAILED, DATASET_FILE_DOWNLOAD_FAILED);
            }
        }

        List<String> inputFiles = taskDescription.getInputFiles();
        if (inputFiles != null && !dataService.downloadFiles(chainTaskId,
                inputFiles)) {
            return triggerPostComputeHookOnError(chainTaskId, context,
                    taskDescription, DATA_DOWNLOAD_FAILED, INPUT_FILES_DOWNLOAD_FAILED);
        }

        return ReplicateActionResponse.success();
    }

    private ReplicateActionResponse triggerPostComputeHookOnError(String chainTaskId,
                                                                  String context,
                                                                  TaskDescription taskDescription,
                                                                  ReplicateStatus errorStatus,
                                                                  ReplicateStatusCause errorCause) {
        if (resultService.writeErrorToIexecOut(chainTaskId, errorStatus, errorCause) &&
                computeManagerService.runPostCompute(taskDescription, "").isSuccessful()) {
            //Graceful error, worker will be prompt to contribute
            logError(errorCause, context, chainTaskId);
            return ReplicateActionResponse.failure(errorCause);
        }
        //Download failed hard, worker cannot contribute
        logError(POST_COMPUTE_FAILED, context, chainTaskId);
        return ReplicateActionResponse.failure(POST_COMPUTE_FAILED);
    }

    ReplicateActionResponse compute(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "compute";
        if (oErrorStatus.isPresent()) {
            return getFailureResponseAndPrintError(oErrorStatus.get(), context, chainTaskId);
        }

        TaskDescription taskDescription =
                iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            return getFailureResponseAndPrintError(TASK_DESCRIPTION_NOT_FOUND,
                    context, chainTaskId);
        }

        if (!computeManagerService.isAppDownloaded(taskDescription.getAppUri())) {
            return getFailureResponseAndPrintError(APP_NOT_FOUND_LOCALLY,
                    context, chainTaskId);
        }

        WorkerpoolAuthorization workerpoolAuthorization =
                contributionService.getWorkerpoolAuthorization(chainTaskId);

        PreComputeResponse preResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        if (!preResponse.isSuccessful()) {
            return getFailureResponseAndPrintError(PRE_COMPUTE_FAILED,
                    context, chainTaskId);
        }

        AppComputeResponse appResponse =
                computeManagerService.runCompute(taskDescription,
                        preResponse.getSecureSessionId());
        if (!appResponse.isSuccessful()) {
            logError("app compute error", context, chainTaskId);
            return ReplicateActionResponse.failureWithStdout(appResponse.getStdout());
        }

        PostComputeResponse postResponse =
                computeManagerService.runPostCompute(taskDescription,
                        preResponse.getSecureSessionId());
        if (!postResponse.isSuccessful()) {
            logError("post compute error", context, chainTaskId);
            return ReplicateActionResponse.failureWithStdout(POST_COMPUTE_FAILED,
                    postResponse.getStdout());
        }
        return ReplicateActionResponse.successWithStdout(preResponse.getStdout() +
                "\n" + appResponse.getStdout() + "\n" + postResponse.getStdout());
    }

    ReplicateActionResponse contribute(String chainTaskId) {
        Optional<ReplicateStatusCause> oErrorStatus =
                contributionService.getCannotContributeStatusCause(chainTaskId);
        String context = "contribute";
        if (oErrorStatus.isPresent()) {
            return getFailureResponseAndPrintError(oErrorStatus.get(),
                    context, chainTaskId);
        }

        TaskDescription taskDescription =
                iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null) {
            return getFailureResponseAndPrintError(TASK_DESCRIPTION_NOT_FOUND,
                    context, chainTaskId);
        }

        if (!hasEnoughGas()) {
            return getFailureResponseAndPrintError(OUT_OF_GAS,
                    context, chainTaskId);
        }

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        if (computedFile == null) {
            logError("computed file error", context, chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        Contribution contribution =
                contributionService.getContribution(computedFile);
        if (contribution == null) {
            logError("get contribution error", context, chainTaskId);
            return ReplicateActionResponse.failure(ENCLAVE_SIGNATURE_NOT_FOUND);//TODO update status
        }

        Optional<ChainReceipt> oChainReceipt =
                contributionService.contribute(contribution);

        if (oChainReceipt.isEmpty() ||
                !isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID);
        }

        return ReplicateActionResponse.success(oChainReceipt.get());
    }

    ReplicateActionResponse reveal(String chainTaskId,
                                   TaskNotificationExtra extra) {
        String context = "reveal";
        if (extra == null || extra.getBlockNumber() == 0) {
            return getFailureResponseAndPrintError(CONSENSUS_BLOCK_MISSING,
                    context, chainTaskId);
        }
        long consensusBlock = extra.getBlockNumber();

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String resultDigest = computedFile != null ?
                computedFile.getResultDigest() : "";

        if (resultDigest.isEmpty()) {
            logError("get result digest error", context, chainTaskId);
            return ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND);
        }

        if (!revealService.isConsensusBlockReached(chainTaskId,
                consensusBlock)) {
            return getFailureResponseAndPrintError(BLOCK_NOT_REACHED,
                    context, chainTaskId
            );
        }

        if (!revealService.repeatCanReveal(chainTaskId,
                resultDigest)) {
            return getFailureResponseAndPrintError(CANNOT_REVEAL,
                    context, chainTaskId);
        }

        if (!hasEnoughGas()) {
            logError(OUT_OF_GAS, context, chainTaskId);
            // Don't we prefer an OUT_OF_GAS?
            System.exit(0);
        }

        Optional<ChainReceipt> oChainReceipt =
                revealService.reveal(chainTaskId, resultDigest);
        if (oChainReceipt.isEmpty() ||
                !isValidChainReceipt(chainTaskId, oChainReceipt)) {
            return getFailureResponseAndPrintError(CHAIN_RECEIPT_NOT_VALID,
                    context, chainTaskId
            );
        }

        return ReplicateActionResponse.success(oChainReceipt.get());
    }

    ReplicateActionResponse uploadResult(String chainTaskId) {
        String resultLink = resultService.uploadResultAndGetLink(chainTaskId);
        String context = "upload result";
        if (resultLink.isEmpty()) {
            return getFailureResponseAndPrintError(RESULT_LINK_MISSING,
                    context, chainTaskId
            );
        }

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String callbackData = computedFile != null ?
                computedFile.getCallbackData() : "";

        log.info("Result uploaded [chainTaskId:{}, resultLink:{}, callbackData:{}]",
                chainTaskId, resultLink, callbackData);

        return ReplicateActionResponse.success(resultLink, callbackData);
    }

    ReplicateActionResponse complete(String chainTaskId) {
        if (!resultService.removeResult(chainTaskId)) {
            return ReplicateActionResponse.failure();
        }

        return ReplicateActionResponse.success();
    }

    boolean abort(String chainTaskId) {
        return resultService.removeResult(chainTaskId);
    }

    boolean hasEnoughGas() {
        if (iexecHubService.hasEnoughGas()) {
            return true;
        }

        String noEnoughGas = String.format("Out of gas! please refill your " +
                        "wallet [walletAddress:%s]",
                workerConfigurationService.getWorkerWalletAddress());
        LoggingUtils.printHighlightedMessage(noEnoughGas);
        return false;
    }

    //TODO Move that to contribute & reveal services
    boolean isValidChainReceipt(String chainTaskId,
                                Optional<ChainReceipt> oChainReceipt) {
        if (oChainReceipt.isEmpty()) {
            log.warn("The chain receipt is empty, nothing will be sent to the" +
                    " core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (oChainReceipt.get().getBlockNumber() == 0) {
            log.warn("The blockNumber of the receipt is equal to 0, status " +
                    "will not be updated in the core [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    private ReplicateActionResponse getFailureResponseAndPrintError(ReplicateStatusCause cause, String context, String chainTaskId) {
        logError(cause, context, chainTaskId);
        return ReplicateActionResponse.failure(cause);
    }

    /**
     * This method, which a <String> 'cause' should disappear at some point
     * Each error should have it proper ReplicateStatusCause so the core could
     * keep track of it.
     */
    private void logError(String cause, String failureContext,
                          String chainTaskId) {
        log.error("Failed to {} [chainTaskId:'{}', cause:'{}']", failureContext,
                chainTaskId, cause);
    }

    private void logError(ReplicateStatusCause cause, String failureContext,
                          String chainTaskId) {
        logError(cause != null ? cause.toString() : "", failureContext,
                chainTaskId);
    }

}