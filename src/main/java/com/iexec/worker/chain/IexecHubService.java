package com.iexec.worker.chain;


import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.feign.CoreWorkerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


@Slf4j
@Service
public class IexecHubService {

    // NULL variables since no SGX usage for now
    private static final String EMPTY_ENCLAVE_CHALLENGE = "0x0000000000000000000000000000000000000000";
    private static final String EMPTY_HEXASTRING_64 = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private final IexecHubABILegacy iexecHub;
    private final CredentialsService credentialsService;
    private final IexecClerkABILegacy iexecClerk;
    private final ThreadPoolExecutor executor;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           CoreWorkerClient coreWorkerClient) throws ExecutionException, InterruptedException {
        this.credentialsService = credentialsService;
        this.iexecHub = ChainUtils.loadHubContract(
                credentialsService.getCredentials(),
                ChainUtils.getWeb3j(coreWorkerClient.getPublicConfiguration().getBlockchainURL()),
                coreWorkerClient.getPublicConfiguration().getIexecHubAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentialsService.getCredentials(),
                ChainUtils.getWeb3j(coreWorkerClient.getPublicConfiguration().getBlockchainURL()),
                coreWorkerClient.getPublicConfiguration().getIexecHubAddress());
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);


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

    private String getWorkerAffectation(String worker) {
        String workerAffectation = "";
        try {
            workerAffectation = iexecHub.viewAffectation(worker).send();
        } catch (Exception e) {
            log.error("Failed to get worker affectation [worker:{}]", worker);
        }

        if (workerAffectation.equals("0x0000000000000000000000000000000000000000")) {
            workerAffectation = "";
        }
        return workerAffectation;
    }

    private boolean subscribeToPool(String poolAddress) throws ExecutionException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requested  subscribe [pool:{}, waitingTxCount:{}]", poolAddress, getWaitingTransactionCount());
            return sendSubscribeTransaction(poolAddress);
        }, executor).get();
    }

    private Boolean sendSubscribeTransaction(String poolAddress) {
        IexecHubABILegacy.WorkerSubscriptionEventResponse subscribeEvent = null;
        try {
            RemoteCall<TransactionReceipt> subscribeCall = iexecHub.subscribe(poolAddress);
            log.info("Sent subscribe [pool:{}]", poolAddress);
            TransactionReceipt subscribeReceipt = subscribeCall.send();
            List<IexecHubABILegacy.WorkerSubscriptionEventResponse> workerSubscriptionEvents = iexecHub.getWorkerSubscriptionEvents(subscribeReceipt);
            if (workerSubscriptionEvents != null && !workerSubscriptionEvents.isEmpty()) {
                log.info("Subscribed [pool:{}]", workerSubscriptionEvents.get(0).workerpool);
                subscribeEvent = workerSubscriptionEvents.get(0);
            }
        } catch (Exception e) {
            log.info("Failed subscribe [pool:{}]", poolAddress);
        }
        return subscribeEvent != null;
    }


    IexecHubABILegacy.TaskContributeEventResponse contribute(ContributionAuthorization contribAuth, String contributionHash, String seal) throws ExecutionException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requested  contribute [chainTaskId:{}, waitingTxCount:{}]", contribAuth.getChainTaskId(), getWaitingTransactionCount());
            return sendContributeTransaction(contribAuth, contributionHash, seal);
        }, executor).get();
    }

    private IexecHubABILegacy.TaskContributeEventResponse sendContributeTransaction(ContributionAuthorization contribAuth, String contributionHash, String seal) {
        // No SGX used for now
        IexecHubABILegacy.TaskContributeEventResponse contributeEvent = null;
        try {

            RemoteCall<TransactionReceipt> contributeCall = iexecHub.contributeABILegacy(
                    BytesUtils.stringToBytes(contribAuth.getChainTaskId()),
                    BytesUtils.stringToBytes(contributionHash),
                    BytesUtils.stringToBytes(seal),
                    EMPTY_ENCLAVE_CHALLENGE,
                    BigInteger.valueOf(0),
                    BytesUtils.stringToBytes(EMPTY_HEXASTRING_64),
                    BytesUtils.stringToBytes(EMPTY_HEXASTRING_64),
                    BigInteger.valueOf(contribAuth.getSignV()),
                    contribAuth.getSignR(),
                    contribAuth.getSignS());
            log.info("Sent contribute [chainTaskId:{}, contributionHash:{}]", contribAuth.getChainTaskId(), contributionHash);
            TransactionReceipt contributeReceipt = contributeCall.send();
            if (!iexecHub.getTaskContributeEvents(contributeReceipt).isEmpty()) {
                log.info("Contributed [chainTaskId:{}, contributionHash:{}]", contribAuth.getChainTaskId(), contributionHash);
                contributeEvent = iexecHub.getTaskContributeEvents(contributeReceipt).get(0);
            }
        } catch (Exception e) {
            log.error("Failed contribute [chainTaskId:{}]", contribAuth.getChainTaskId());
        }
        return contributeEvent;
    }

    IexecHubABILegacy.TaskRevealEventResponse reveal(String chainTaskId, String resultDigest) throws ExecutionException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requested  reveal [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
            return sendRevealTransaction(chainTaskId, resultDigest);
        }, executor).get();
    }

    private IexecHubABILegacy.TaskRevealEventResponse sendRevealTransaction(String chainTaskId, String resultDigest) {
        IexecHubABILegacy.TaskRevealEventResponse revealEvent = null;
        try {
            RemoteCall<TransactionReceipt> revealCall = iexecHub.reveal(
                    BytesUtils.stringToBytes(chainTaskId),
                    BytesUtils.stringToBytes(resultDigest));
            log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);
            TransactionReceipt revealReceipt = revealCall.send();
            if (!iexecHub.getTaskRevealEvents(revealReceipt).isEmpty()) {
                log.info("Revealed [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);
                revealEvent = iexecHub.getTaskRevealEvents(revealReceipt).get(0);
            }
        } catch (Exception e) {
            log.error("Reveal Failed [chainTaskId:{}]", chainTaskId);
        }
        return revealEvent;
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - 1 - executor.getCompletedTaskCount();
    }

    Optional<ChainDeal> getChainDeal(String chainDealId) {
        return ChainUtils.getChainDeal(iexecClerk, chainDealId);
    }

    Optional<ChainTask> getChainTask(String chainTaskId) {
        return ChainUtils.getChainTask(iexecHub, chainTaskId);
    }


    Optional<ChainAccount> getChainAccount() {
        return ChainUtils.getChainAccount(iexecClerk, credentialsService.getCredentials().getAddress());
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
            log.error("The chainContribution couldn't be retrieved from the chain [chainTaskId:{}, error:{}]", chainTaskId, e.getLocalizedMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }


}
