package com.iexec.worker.chain;


import com.iexec.common.chain.ChainUtils;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.feign.CoreWorkerClient;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple10;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;


@Slf4j
@Service
public class IexecHubService {

    private final IexecHubABILegacy iexecHub;

    @Autowired
    public IexecHubService(CredentialsService credentialsService, CoreWorkerClient coreWorkerClient) {
        iexecHub = ChainUtils.loadHubContract(
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

    public boolean isTaskInitialized(String chainTaskId) {
        try {
            byte[] bytesChainTaskId = BytesUtils.stringToBytes(chainTaskId);
            Tuple10<BigInteger, byte[], BigInteger, BigInteger, byte[], BigInteger, BigInteger, BigInteger, List<String>, byte[]> receipt = iexecHub.viewTaskABILegacy(bytesChainTaskId).send();
            if (receipt != null && receipt.getSize() > 0) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public String computeHash(String chainTaskId, String consensusHash) {
        byte[] res = Arrays.concatenate(
                BytesUtils.stringToBytes(chainTaskId),
                BytesUtils.stringToBytes(consensusHash));
        return Numeric.toHexString(Hash.sha3(res));
    }

    private String computeSeal(String walletAddress, String chainTaskId, String consensusHash) {
        // concatenate 3 byte[] fields
        byte[] res = Arrays.concatenate(
                BytesUtils.stringToBytes(walletAddress),
                BytesUtils.stringToBytes(chainTaskId),
                BytesUtils.stringToBytes(consensusHash));

        // Hash the result and convert to String
        return Numeric.toHexString(Hash.sha3(res));
    }

    public boolean contribute(ContributionAuthorization contribAuth, String consensusHash) throws Exception {

        String seal = computeSeal(contribAuth.getWorkerWallet(), contribAuth.getChainTaskId(), consensusHash);

        // public RemoteCall<TransactionReceipt> contributeABILegacy(
        // byte[] _taskid,
        // byte[] _resultHash,
        // byte[] _resultSeal,
        // String _enclaveChallenge,
        // BigInteger _enclaveSign_v,
        // byte[] _enclaveSign_r,
        // byte[] _enclaveSign_s,
        // BigInteger _poolSign_v,
        // byte[] _poolSign_r,
        // byte[] _poolSign_s) {

        // Transaction sent in synchron mode for now
        log.info("*** chainTaskId: " + contribAuth.getChainTaskId());
        log.info("*** consensusHash: " + consensusHash);
        log.info("*** seal: " + seal);

        TransactionReceipt receipt = iexecHub.contributeABILegacy(
                BytesUtils.stringToBytes(contribAuth.getChainTaskId()),
                BytesUtils.stringToBytes(consensusHash),
                BytesUtils.stringToBytes(seal),
                "0x0000000000000000000000000000000000000000",
                BigInteger.valueOf(0),
                BytesUtils.stringToBytes("0x0000000000000000000000000000000000000000000000000000000000000000"),
                BytesUtils.stringToBytes("0x0000000000000000000000000000000000000000000000000000000000000000"),
                BigInteger.valueOf(contribAuth.getSignV()),
                contribAuth.getSignR(),
                contribAuth.getSignS()).send();


        return receipt != null && receipt.isStatusOK();
    }
}
