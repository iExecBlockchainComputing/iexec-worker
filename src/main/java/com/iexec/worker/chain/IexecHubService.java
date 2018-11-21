package com.iexec.worker.chain;


import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainUtils;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.feign.CoreWorkerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
public class IexecHubService {

    // NULL variables since no SGX usage for now
    private static final String EMPTY_ENCLAVE_CHALLENGE = "0x0000000000000000000000000000000000000000";
    private static final String EMPTY_HEXASTRING_64 = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private final IexecHubABILegacy iexecHub;
    private final CredentialsService credentialsService;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           CoreWorkerClient coreWorkerClient) {
        this.credentialsService = credentialsService;
        this.iexecHub = ChainUtils.loadHubContract(
                credentialsService.getCredentials(),
                ChainUtils.getWeb3j(coreWorkerClient.getPublicConfiguration().getBlockchainURL()),
                coreWorkerClient.getPublicConfiguration().getIexecHubAddress());

        startWatchers();

        String oldPool = getWorkerAffectation(credentialsService.getCredentials().getAddress());
        String newPool = coreWorkerClient.getPublicConfiguration().getWorkerPoolAddress();

        if (oldPool.isEmpty()) {
            subscribeToPool(newPool);
        } else if (oldPool.equals(newPool)) {
            log.info("Already registered to pool [pool:{}]", newPool);
        } else {
            //TODO: unsubscribe from last and subscribe to current
        }
    }

    private void startWatchers() {
        iexecHub.workerSubscriptionEventObservable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
                .subscribe(workerSubscriptionEventResponse ->
                        log.info("(watcher) Subscribed to pool [pool:{}, worker:{}]", workerSubscriptionEventResponse.pool, workerSubscriptionEventResponse.worker)
                );
    }

    private void subscribeToPool(String poolAddress) {
        try {
            log.info("Subscribing to pool [pool:{}]", poolAddress);
            TransactionReceipt subscribeReceipt = iexecHub.subscribe(poolAddress).send();
            List<IexecHubABILegacy.WorkerSubscriptionEventResponse> workerSubscriptionEvents = iexecHub.getWorkerSubscriptionEvents(subscribeReceipt);
            if (workerSubscriptionEvents != null && !workerSubscriptionEvents.isEmpty()) {
                log.info("Subscribed to pool [pool:{}, worker:{}]", workerSubscriptionEvents.get(0).pool, workerSubscriptionEvents.get(0).worker);
            }
        } catch (Exception e) {
            log.info("Failed to subscribed to pool [pool:{}]", poolAddress);

        }
    }

    private String getWorkerAffectation(String worker) {
        String workerAffectation = "";
        try {
            workerAffectation = iexecHub.viewAffectation(worker).send();
        } catch (Exception e) {
            log.info("Failed to get worker affectation [worker:{}]", worker);
        }

        if (workerAffectation.equals("0x0000000000000000000000000000000000000000")) {
            workerAffectation = "";
        }
        log.info("Got worker pool affectation [pool:{}, worker:{}]", workerAffectation, worker);
        return workerAffectation;
    }

    Optional<ChainTask> getChainTask(String chainTaskId) {
        try {
            ChainTask chainTask = ChainTask.tuple2ChainTask(iexecHub.viewTaskABILegacy(BytesUtils.stringToBytes(chainTaskId)).send());
            if (chainTask != null) {
                return Optional.of(chainTask);
            }
        } catch (Exception e) {
            log.error("The chainTask could't be retrieved from the chain [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }

        return Optional.empty();
    }

    Optional<ChainContribution> getChainContribution(String chainTaskId) {
        String workerAddress = credentialsService.getCredentials().getAddress();

        try {
            ChainContribution chainContribution = ChainContribution.tuple2Contribution(
                    iexecHub.viewContributionABILegacy(BytesUtils.stringToBytes(chainTaskId), workerAddress).send());
            if (chainContribution != null) {
                return Optional.of(chainContribution);
            }
        } catch (Exception e) {
            log.error("The chainContribution couldn't be retrieved from the chain [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }

        return Optional.empty();
    }

    TransactionReceipt contribute(ContributionAuthorization contribAuth, String contributionHash, String seal) throws Exception {
        // No SGX used for now
        return iexecHub.contributeABILegacy(
                BytesUtils.stringToBytes(contribAuth.getChainTaskId()),
                BytesUtils.stringToBytes(contributionHash),
                BytesUtils.stringToBytes(seal),
                EMPTY_ENCLAVE_CHALLENGE,
                BigInteger.valueOf(0),
                BytesUtils.stringToBytes(EMPTY_HEXASTRING_64),
                BytesUtils.stringToBytes(EMPTY_HEXASTRING_64),
                BigInteger.valueOf(contribAuth.getSignV()),
                contribAuth.getSignR(),
                contribAuth.getSignS())
                .send();
    }

    TransactionReceipt reveal(String taskId, String resultDigest) throws Exception {
        return iexecHub.reveal(
                BytesUtils.stringToBytes(taskId),
                BytesUtils.stringToBytes(resultDigest))
                .send();
    }
}
