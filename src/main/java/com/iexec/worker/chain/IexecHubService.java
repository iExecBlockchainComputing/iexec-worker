package com.iexec.worker.chain;


import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.worker.feign.CoreWorkerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.ens.EnsResolutionException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.util.List;


@Slf4j
@Service
public class IexecHubService {

    private final IexecHubABILegacy iexecHub;

    @Autowired
    public IexecHubService(CredentialsService credentialsService, CoreWorkerClient coreWorkerClient) {
        iexecHub = loadHubContract(
                credentialsService.getCredentials(),
                getWeb3j(coreWorkerClient.getPublicConfiguration().getBlockchainURL()),
                coreWorkerClient.getPublicConfiguration().getIexecHubAddress());

        startWatchers();

        String oldPool = getWorkerAffectation(credentialsService.getCredentials().getAddress());
        String newPool = coreWorkerClient.getPublicConfiguration().getWorkerPoolAddress();

        if (oldPool.isEmpty()){
            subscribeToPool(newPool);
        } else if (oldPool.equals(newPool)) {
            log.info("Already registered to pool [pool:{}]", newPool);
        } else {
            //TODO: unsubscribe from last and subscribe to current
        }


    }

    private IexecHubABILegacy loadHubContract(Credentials credentials, Web3j web3j, String iexecHubAddress) {
        ExceptionInInitializerError exceptionInInitializerError = new ExceptionInInitializerError("Failed to load IexecHub contract from address " + iexecHubAddress);

        if (iexecHubAddress != null && !iexecHubAddress.isEmpty()) {
            try {
                IexecHubABILegacy iexecHub = IexecHubABILegacy.load(
                        iexecHubAddress, web3j, credentials, new DefaultGasProvider());
                //if (!iexecHub.isValid()){ throw exceptionInInitializerError;}

                log.info("Loaded contract IexecHub [address:{}] ", iexecHubAddress);
                return iexecHub;
            } catch (EnsResolutionException e) {
                throw exceptionInInitializerError;
            }
        } else {
            throw exceptionInInitializerError;
        }
    }

    private Web3j getWeb3j(String chainNodeAddress) {
        Web3j web3j = Web3j.build(new HttpService(chainNodeAddress));
        ExceptionInInitializerError exceptionInInitializerError = new ExceptionInInitializerError("Failed to connect to ethereum node " + chainNodeAddress);
        try {
            log.info("Connected to Ethereum node [node:{}]", web3j.web3ClientVersion().send().getWeb3ClientVersion());
            if (web3j.web3ClientVersion().send().getWeb3ClientVersion() == null) {
                throw exceptionInInitializerError;
            }
        } catch (IOException e) {
            throw exceptionInInitializerError;
        }
        return web3j;
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
            if (workerSubscriptionEvents != null && workerSubscriptionEvents.size() > 0) {
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

}
