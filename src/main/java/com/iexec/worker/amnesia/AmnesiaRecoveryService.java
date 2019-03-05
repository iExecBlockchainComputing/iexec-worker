package com.iexec.worker.amnesia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.RecoverableAction;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.InterruptedReplicatesModel;
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


    public AmnesiaRecoveryService(CustomFeignClient customFeignClient,
                                  SubscriptionService subscriptionService,
                                  ReplicateService replicateService,
                                  ResultService resultService) {
        this.customFeignClient = customFeignClient;
        this.subscriptionService = subscriptionService;
        this.replicateService = replicateService;
        this.resultService = resultService;
    }

    public void recoverInterruptedReplicates() {
        InterruptedReplicatesModel interruptedReplicatesModel = customFeignClient.getInterruptedReplicates();

        List<ContributionAuthorization> revealNeededList = interruptedReplicatesModel.getRevealNeededList();
        List<ContributionAuthorization> resultUploadNeededList = interruptedReplicatesModel.getResultUploadNeededList();
        List<ContributionAuthorization> contributionNeededList = interruptedReplicatesModel.getContributionNeededList();

        recoverTasksAndNotifyCore(revealNeededList, RecoverableAction.REVEAL);
        recoverTasksAndNotifyCore(resultUploadNeededList, RecoverableAction.RESULT_UPLOAD);
        recoverTasksWithContributionNeeded(contributionNeededList);
    }

    private void recoverTasksAndNotifyCore(List<ContributionAuthorization> list, RecoverableAction interruptedAction) {
        List<String> recoveredTasks = new ArrayList<>();

        for (ContributionAuthorization contributionAuth : list) {
            if (!resultService.isResultZipFound(contributionAuth.getChainTaskId())) {
                continue;
            }

            log.info("interrupted task found [chainTaskId:{}, interruptedAction:{}]",
                    contributionAuth.getChainTaskId(), interruptedAction);

            Optional<AvailableReplicateModel> oReplicateModel =
                    replicateService.retrieveAvailableReplicateModelFromContribAuth(contributionAuth);
            if (!oReplicateModel.isPresent()) {
                log.info("could not retrieve replicateModel from contributionAuth [chainTaskId:{}]",
                        contributionAuth.getChainTaskId());
                continue;
            }

            resultService.saveResultInfo(contributionAuth.getChainTaskId(), oReplicateModel.get());
            subscriptionService.subscribeToTopic(contributionAuth.getChainTaskId());
            recoveredTasks.add(contributionAuth.getChainTaskId());
        }

        if (!recoveredTasks.isEmpty()) {
            customFeignClient.notifyOfRecovery(interruptedAction, recoveredTasks);
        }
    }

    private void recoverTasksWithContributionNeeded(List<ContributionAuthorization> contributionNeededList) {
        for (ContributionAuthorization contributionAuth : contributionNeededList) {
            log.info("interrupted task found [chainTaskId:{}, interruptedAction:contribution]", contributionAuth.getChainTaskId());
            replicateService.createReplicateFromContributionAuth(contributionAuth);
        }
    }

}