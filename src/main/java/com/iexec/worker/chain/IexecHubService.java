package com.iexec.worker.chain;


import com.iexec.common.chain.ChainAccount;
import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.worker.config.PublicConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.utils.BytesUtils.stringToBytes;


@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService {

    private final CredentialsService credentialsService;
    private final ThreadPoolExecutor executor;
    private final Web3j web3j;
    private Web3jService web3jService;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           PublicConfigurationService publicConfigurationService) {
        super(credentialsService.getCredentials(), web3jService, publicConfigurationService.getIexecHubAddress());
        this.credentialsService = credentialsService;
        this.web3j = web3jService.getWeb3j();
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }
    
    IexecHubABILegacy.TaskContributeEventResponse contribute(ContributionAuthorization contribAuth,
                                                             String resultHash,
                                                             String resultSeal,
                                                             Sign.SignatureData enclaveSignatureData) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Requested  contribute [chainTaskId:{}, waitingTxCount:{}]",
                        contribAuth.getChainTaskId(), getWaitingTransactionCount());
                return sendContributeTransaction(contribAuth, resultHash, resultSeal, enclaveSignatureData);
            }, executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private IexecHubABILegacy.TaskContributeEventResponse sendContributeTransaction(ContributionAuthorization contribAuth,
                                                                                    String resultHash,
                                                                                    String resultSeal,
                                                                                    Sign.SignatureData enclaveSignatureData) {
        IexecHubABILegacy.TaskContributeEventResponse contributeEvent = null;

        try {
            RemoteCall<TransactionReceipt> contributeCall = getHubContract(web3jService.getWritingContractGasProvider()).contributeABILegacy(
                    stringToBytes(contribAuth.getChainTaskId()),
                    stringToBytes(resultHash),
                    stringToBytes(resultSeal),
                    contribAuth.getEnclave(),
                    BigInteger.valueOf(enclaveSignatureData.getV()),
                    enclaveSignatureData.getR(),
                    enclaveSignatureData.getS(),
                    BigInteger.valueOf(contribAuth.getSignV()),
                    contribAuth.getSignR(),
                    contribAuth.getSignS());
            log.info("Sent contribute [chainTaskId:{}, resultHash:{}]", contribAuth.getChainTaskId(), resultHash);
            TransactionReceipt contributeReceipt = contributeCall.send();
            if (!getHubContract(web3jService.getWritingContractGasProvider()).getTaskContributeEvents(contributeReceipt).isEmpty()) {
                log.info("Contributed [chainTaskId:{}, resultHash:{}, gasUsed:{}]",
                        contribAuth.getChainTaskId(), resultHash, contributeReceipt.getGasUsed());
                contributeEvent = getHubContract(web3jService.getWritingContractGasProvider()).getTaskContributeEvents(contributeReceipt).get(0);
            }
        } catch (Exception e) {
            log.error("Failed contribute [chainTaskId:{}, exception:{}]", contribAuth.getChainTaskId(), e.getMessage());
            e.printStackTrace();
        }
        return contributeEvent;
    }

    IexecHubABILegacy.TaskRevealEventResponse reveal(String chainTaskId, String resultDigest) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Requested  reveal [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
                return sendRevealTransaction(chainTaskId, resultDigest);
            }, executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private IexecHubABILegacy.TaskRevealEventResponse sendRevealTransaction(String chainTaskId, String resultDigest) {
        IexecHubABILegacy.TaskRevealEventResponse revealEvent = null;
        try {
            RemoteCall<TransactionReceipt> revealCall = getHubContract(web3jService.getWritingContractGasProvider()).reveal(
                    stringToBytes(chainTaskId),
                    stringToBytes(resultDigest));
            log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);
            TransactionReceipt revealReceipt = revealCall.send();
            if (!getHubContract(web3jService.getWritingContractGasProvider()).getTaskRevealEvents(revealReceipt).isEmpty()) {
                log.info("Revealed [chainTaskId:{}, resultDigest:{}, gasUsed:{}]",
                        chainTaskId, resultDigest, revealReceipt.getGasUsed());
                revealEvent = getHubContract(web3jService.getWritingContractGasProvider()).getTaskRevealEvents(revealReceipt).get(0);
            }
        } catch (Exception e) {
            log.error("Reveal Failed [chainTaskId:{}, exception:{}]", chainTaskId, e.getMessage());
            e.printStackTrace();
        }
        return revealEvent;
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - 1 - executor.getCompletedTaskCount();
    }

    Optional<ChainContribution> getChainContribution(String chainTaskId) {
        return getChainContribution(chainTaskId, credentialsService.getCredentials().getAddress());
    }

    Optional<ChainAccount> getChainAccount() {
        return getChainAccount(credentialsService.getCredentials().getAddress());
    }

    public boolean hasEnoughGas() {
        return web3jService.hasEnoughGas(credentialsService.getCredentials().getAddress());
    }

    public long getLastBlock() {
        try {
            return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber().longValue();
        } catch (IOException e) {
            log.error("GetLastBlock failed");
        }
        return 0;
    }


}
