package com.iexec.worker.amnesia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
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

    public AmnesiaRecoveryService(CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ReplicateService replicateService,
                                  ResultService resultService,
                                  TaskExecutorService taskExecutorService) {
        this.customFeignClient = customFeignClient;
        this.subscriptionService = subscriptionService;
        this.replicateService = replicateService;
        this.resultService = resultService;
        this.taskExecutorService = taskExecutorService;
    }

    public List<String> recoverInterruptedReplicates() {
        List<InterruptedReplicateModel> interruptedReplicates = customFeignClient.getInterruptedReplicates();
        List<String> recoveredChainTaskIds = new ArrayList<>();

        if (interruptedReplicates.isEmpty()) {
            log.info("no interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (InterruptedReplicateModel interruptedReplicate : interruptedReplicates) {

            ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
            String chainTaskId = contributionAuth.getChainTaskId();
            RecoveryAction recoveryAction = interruptedReplicate.getRecoverableAction();

            log.info("recovering interrupted task [chainTaskId:{}, recoveryAction:{}]",
                chainTaskId, recoveryAction);

            Optional<AvailableReplicateModel> oReplicateModel =
            replicateService.retrieveAvailableReplicateModelFromContribAuth(contributionAuth);

            if (!oReplicateModel.isPresent()) {
                log.info("could not retrieve replicateModel from contributionAuth to recover task [chainTaskId:{}, RecoveryAction:{}]",
                        chainTaskId, recoveryAction);
                continue;
            }

            AvailableReplicateModel replicateModel = oReplicateModel.get();

            if (interruptedReplicate.getRecoverableAction().equals(RecoveryAction.CONTRIBUTE)) {
                recoverReplicateByContributing(contributionAuth, replicateModel);
                continue;
            }

            if (interruptedReplicate.getRecoverableAction().equals(RecoveryAction.REVEAL)) {
                recoverReplicateByRevealing(chainTaskId, replicateModel);
                continue;
            }

            if (interruptedReplicate.getRecoverableAction().equals(RecoveryAction.UPLOAD_RESULT)) {
                recoverReplicateByUploadingResult(chainTaskId, replicateModel);
            }

            recoveredChainTaskIds.add(chainTaskId);
        }

        return recoveredChainTaskIds;
    }

    public void recoverReplicateByContributing(ContributionAuthorization contributionAuth, AvailableReplicateModel replicateModel) {
        String chainTaskId = contributionAuth.getChainTaskId();

        if (resultService.isResultZipFound(chainTaskId)) {
            taskExecutorService.tryToContribute(contributionAuth);
            return;
        }

        if (resultService.isResultFolderFound(chainTaskId)) {
            resultService.zipResultFolder(chainTaskId);
            resultService.saveResultInfo(chainTaskId, replicateModel);
            taskExecutorService.tryToContribute(contributionAuth);
            return;
        }

        // re-run computation
        taskExecutorService.addReplicate(contributionAuth);
        return;
    }

    public void recoverReplicateByRevealing(String chainTaskId, AvailableReplicateModel replicateModel) {

        boolean resultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean resultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!resultZipFound && !resultFolderFound) {
            log.error("couldn't recover task by revealing since result was not found "
                    + "[chainTaskId:{}]", chainTaskId);
            return;
        }

        subscriptionService.subscribeToTopic(chainTaskId);

        if (resultZipFound) {
            taskExecutorService.reveal(chainTaskId);
            return;
        }

        resultService.zipResultFolder(chainTaskId);
        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.reveal(chainTaskId);
    }

    public void recoverReplicateByUploadingResult(String chainTaskId, AvailableReplicateModel replicateModel) {

        boolean resultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean resultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!resultZipFound && !resultFolderFound) {
            log.error("couldn't recover task by uploading since result was not found "
                    + "[chainTaskId:{}]", chainTaskId);
            return;
        }

        subscriptionService.subscribeToTopic(chainTaskId);

        if (resultZipFound) {
            taskExecutorService.uploadResult(chainTaskId);
            return;
        }

        resultService.zipResultFolder(chainTaskId);
        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.uploadResult(chainTaskId);
    }
}