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

        if (interruptedReplicates == null || interruptedReplicates.isEmpty()) {
            log.info("no interrupted tasks to recover");
            return Collections.emptyList();
        }

        for (InterruptedReplicateModel interruptedReplicate : interruptedReplicates) {

            ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
            RecoveryAction recoveryAction = interruptedReplicate.getRecoveryAction();
            String chainTaskId = contributionAuth.getChainTaskId();

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
            boolean isRecovered = false;

            switch (interruptedReplicate.getRecoveryAction()) {
                case NONE:
                    isRecovered = true; // just subscribe and wait
                    break;

                case CONTRIBUTE:
                    isRecovered = recoverReplicateByContributing(contributionAuth, replicateModel);
                    break;

                case REVEAL:
                    isRecovered = recoverReplicateByRevealing(chainTaskId, replicateModel);
                    break;

                case UPLOAD_RESULT:
                    isRecovered = recoverReplicateByUploadingResult(chainTaskId, replicateModel);
                    break;
            }

            if (isRecovered) {
                subscriptionService.subscribeToTopic(chainTaskId);
                recoveredChainTaskIds.add(chainTaskId);
            }
        }

        return recoveredChainTaskIds;
    }

    public boolean recoverReplicateByContributing(ContributionAuthorization contributionAuth, AvailableReplicateModel replicateModel) {
        String chainTaskId = contributionAuth.getChainTaskId();

        boolean isResultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean isResultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!isResultFolderFound && !isResultZipFound) {
            // re-run computation
            taskExecutorService.addReplicate(contributionAuth, replicateModel);
            return true;
        }

        if (!isResultZipFound) {
            resultService.zipResultFolder(chainTaskId);
        }

        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.tryToContribute(contributionAuth);
        return true;
    }

    public boolean recoverReplicateByRevealing(String chainTaskId, AvailableReplicateModel replicateModel) {

        boolean isResultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean isResultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!isResultZipFound && !isResultFolderFound) {
            log.error("couldn't recover task by revealing since result was not found "
                    + "[chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (!isResultZipFound) {
            resultService.zipResultFolder(chainTaskId);
        }

        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.reveal(chainTaskId);
        return true;
    }

    public boolean recoverReplicateByUploadingResult(String chainTaskId, AvailableReplicateModel replicateModel) {

        boolean isResultZipFound = resultService.isResultZipFound(chainTaskId);
        boolean isResultFolderFound = resultService.isResultFolderFound(chainTaskId);

        if (!isResultZipFound && !isResultFolderFound) {
            log.error("couldn't recover task by uploading since result was not found "
                    + "[chainTaskId:{}]", chainTaskId);
            return false;
        }

        if (!isResultZipFound) {
            resultService.zipResultFolder(chainTaskId);
        }

        resultService.saveResultInfo(chainTaskId, replicateModel);
        taskExecutorService.uploadResult(chainTaskId);
        return true;
    }
}