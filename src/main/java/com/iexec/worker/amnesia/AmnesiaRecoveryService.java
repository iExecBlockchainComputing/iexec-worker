package com.iexec.worker.amnesia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.executor.TaskExecutorService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.replicate.ReplicateService;
import com.iexec.worker.result.ResultService;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


/* 
 * This service is used to remind the worker of possible interrupted works
 * after a restart and how to deal with each interruption
 */
@Slf4j
@Service
public class AmnesiaRecoveryService {

    private CustomFeignClient customFeignClient;
    private SubscriptionService subscriptionService;
    private ReplicateService replicateService;
    private ResultService resultService;
    private TaskExecutorService taskExecutorService;
    private IexecHubService iexecHubService;

    public AmnesiaRecoveryService(CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ReplicateService replicateService,
                                  ResultService resultService,
                                  TaskExecutorService taskExecutorService,
                                  IexecHubService iexecHubService) {
        this.customFeignClient = customFeignClient;
        this.subscriptionService = subscriptionService;
        this.replicateService = replicateService;
        this.resultService = resultService;
        this.taskExecutorService = taskExecutorService;
        this.iexecHubService = iexecHubService;
    }

    public List<String> recoverInterruptedReplicates() {
        long lasAvailableBlockNumber = iexecHubService.getLatestBlockNumber();
        List<InterruptedReplicateModel> interruptedReplicates = customFeignClient.getInterruptedReplicates(
                lasAvailableBlockNumber);
        List<String> recoveredChainTaskIds = new ArrayList<>();

        if (interruptedReplicates == null || interruptedReplicates.isEmpty()) {
            log.info("No interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (InterruptedReplicateModel interruptedReplicate : interruptedReplicates) {

            ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
            RecoveryAction recoveryAction = interruptedReplicate.getRecoveryAction();
            String chainTaskId = contributionAuth.getChainTaskId();
            boolean isResultAvailable = isResultAvailable(chainTaskId);

            log.info("Recovering interrupted task [chainTaskId:{}, recoveryAction:{}]",
                    chainTaskId, recoveryAction);

            if (!isResultAvailable && recoveryAction != RecoveryAction.CONTRIBUTE) {
                log.error("Could not recover task, result not found [chainTaskId:{}, RecoveryAction:{}]",
                        chainTaskId, recoveryAction);
                continue;
            }

            Optional<AvailableReplicateModel> oReplicateModel =
                    replicateService.retrieveAvailableReplicateModelFromContribAuth(contributionAuth);

            if (!oReplicateModel.isPresent()) {
                log.error("Could not recover task, no replicateModel retrieved [chainTaskId:{}, RecoveryAction:{}]",
                        chainTaskId, recoveryAction);
                continue;
            }

            AvailableReplicateModel replicateModel = oReplicateModel.get();
            recoverReplicate(interruptedReplicate, replicateModel);
            recoveredChainTaskIds.add(chainTaskId);
        }

        return recoveredChainTaskIds;
    }

    public void recoverReplicate(InterruptedReplicateModel interruptedReplicate,
                                 AvailableReplicateModel replicateModel) {

        ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();

        switch (interruptedReplicate.getRecoveryAction()) {
            case WAIT:
                subscriptionService.subscribeToTopic(chainTaskId);
                resultService.saveResultInfo(chainTaskId, replicateModel);
                break;

            case CONTRIBUTE:
                subscriptionService.subscribeToTopic(chainTaskId);
                recoverReplicateByContributing(contributionAuth, replicateModel);
                break;

            case ABORT_CONSENSUS_REACHED:
                taskExecutorService.abortConsensusReached(chainTaskId);
                break;

            case ABORT_CONTRIBUTION_TIMEOUT:
                taskExecutorService.abortContributionTimeout(chainTaskId);
                break;

            case REVEAL:
                subscriptionService.subscribeToTopic(chainTaskId);
                resultService.saveResultInfo(chainTaskId, replicateModel);
                taskExecutorService.reveal(chainTaskId);
                break;

            case UPLOAD_RESULT:
                subscriptionService.subscribeToTopic(chainTaskId);
                resultService.saveResultInfo(chainTaskId, replicateModel);
                taskExecutorService.uploadResult(chainTaskId);
                break;

            case COMPLETE:
                taskExecutorService.completeTask(chainTaskId);
                break;

            default:
                break;
        }
    }

    private boolean isResultAvailable(String chainTaskId) {
        boolean isResultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean isResultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!isResultZipFound && !isResultFolderFound) return false;

        if (!isResultZipFound) resultService.zipResultFolder(chainTaskId);

        return true;
    }

    public void recoverReplicateByContributing(ContributionAuthorization contributionAuth,
                                               AvailableReplicateModel replicateModel) {

        String chainTaskId = contributionAuth.getChainTaskId();
        boolean isResultAvailable = isResultAvailable(chainTaskId);

        if (!isResultAvailable) {
            log.info("Result not found, re-running computation to recover task " +
                    "[chainTaskId:{}, recoveryAction:CONTRIBUTE]", chainTaskId);
            taskExecutorService.addReplicate(replicateModel);
            return;
        }

        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.contribute(contributionAuth);
    }
}