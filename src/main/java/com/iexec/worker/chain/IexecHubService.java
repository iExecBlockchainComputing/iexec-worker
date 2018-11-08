package com.iexec.worker.chain;


import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.worker.feign.CoreWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.ens.EnsResolutionException;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;


@Service
public class IexecHubService {

    private static final Logger log = LoggerFactory.getLogger(IexecHubService.class);
    private final IexecHubABILegacy iexecHub;

    @Autowired
    public IexecHubService(CredentialsService credentialsService, CoreWorkerClient coreWorkerClient) {
        iexecHub = loadHubContract(
                credentialsService.getCredentials(),
                getWeb3j(coreWorkerClient.getPublicConfiguration().getBlockchainURL()),
                coreWorkerClient.getPublicConfiguration().getIexecHubAddress());

        startWatchers();
        subscribeToPool(coreWorkerClient.getPublicConfiguration().getWorkerPoolAddress());
    }

    private IexecHubABILegacy loadHubContract(Credentials credentials, Web3j web3j, String iexecHubAddress) {
        ExceptionInInitializerError exceptionInInitializerError = new ExceptionInInitializerError("Failed to load IexecHub contract from address " + iexecHubAddress);
        IexecHubABILegacy iexecHub;

        if (iexecHubAddress != null && !iexecHubAddress.isEmpty()) {
            try {
                iexecHub = IexecHubABILegacy.load(
                        iexecHubAddress, web3j, credentials, new ContractGasProvider() {
                            @Override
                            public BigInteger getGasPrice(String s) {
                                return DefaultGasProvider.GAS_PRICE;
                            }

                            @Override
                            public BigInteger getGasPrice() {
                                return DefaultGasProvider.GAS_PRICE;
                            }

                            @Override
                            public BigInteger getGasLimit(String s) {
                                return DefaultGasProvider.GAS_LIMIT;
                            }

                            @Override
                            public BigInteger getGasLimit() {
                                return DefaultGasProvider.GAS_LIMIT;
                            }
                        });
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
            log.info(web3j.web3ClientVersion().send().getWeb3ClientVersion());
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

}
