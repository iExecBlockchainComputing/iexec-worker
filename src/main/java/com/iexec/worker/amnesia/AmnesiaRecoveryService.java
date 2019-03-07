package com.iexec.worker.amnesia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.RecoverableAction;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveredReplicateModel;
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

    public void recoverInterruptedReplicatesAndNotifyCore() {
        List<InterruptedReplicateModel> interruptedReplicates = customFeignClient.getInterruptedReplicates();
        List<RecoveredReplicateModel> recoveredReplicates = new ArrayList<>();

        if (interruptedReplicates.isEmpty()) {
            log.info("no interrupted tasks to recover");
            return;
        }

        for (InterruptedReplicateModel interruptedReplicate : interruptedReplicates) {

            ContributionAuthorization contributionAuth = interruptedReplicate.getContributionAuthorization();
            String chainTaskId = contributionAuth.getChainTaskId();

            if (interruptedReplicate.getRecoverableAction().equals(RecoverableAction.CONTRIBUTE)) {
                replicateService.createReplicateFromContributionAuth(contributionAuth);
                continue;
            }

            if (!resultService.isResultZipFound(chainTaskId)) {
                continue;
            }

            // TODO: maybe check for result folder

            log.info("recovering interrupted task [chainTaskId:{}, recoverableAction:{}]",
                    chainTaskId, interruptedReplicate.getRecoverableAction());

            Optional<AvailableReplicateModel> oReplicateModel =
            replicateService.retrieveAvailableReplicateModelFromContribAuth(contributionAuth);

            if (!oReplicateModel.isPresent()) {
                log.info("could not retrieve replicateModel from contributionAuth [chainTaskId:{}]", chainTaskId);
                continue;
            }

            resultService.saveResultInfo(chainTaskId, oReplicateModel.get());
            subscriptionService.subscribeToTopic(chainTaskId);

            RecoveredReplicateModel recoveredReplicate = RecoveredReplicateModel.builder()
                    .chainTaskId(chainTaskId)
                    .recoverableAction(interruptedReplicate.getRecoverableAction())
                    .build();

            recoveredReplicates.add(recoveredReplicate);
        }

        if (!recoveredReplicates.isEmpty()) {
            customFeignClient.notifyOfRecovery(recoveredReplicates);
        }
    }
}