package com.iexec.worker.chain;


import com.iexec.common.chain.*;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.contract.generated.App;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.worker.feign.CustomFeignClient;
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
public class IexecHubService {

    private final IexecHubABILegacy iexecHub;
    private final CredentialsService credentialsService;
    private final IexecClerkABILegacy iexecClerk;
    private final ThreadPoolExecutor executor;
    private final Web3j web3j;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           CustomFeignClient customFeignClient) {
        this.credentialsService = credentialsService;
        PublicConfiguration publicConfiguration = customFeignClient.getPublicConfiguration();
        this.web3j = ChainUtils.getWeb3j(publicConfiguration.getBlockchainURL());
        this.iexecHub = ChainUtils.loadHubContract(
                credentialsService.getCredentials(),
                this.web3j,
                publicConfiguration.getIexecHubAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentialsService.getCredentials(),
                this.web3j,
                publicConfiguration.getIexecHubAddress());
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }

    IexecHubABILegacy.TaskContributeEventResponse contribute(ContributionAuthorization contribAuth, String resultHash, String resultSeal, Sign.SignatureData enclaveSignatureData) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                log.info("Requested  contribute [chainTaskId:{}, waitingTxCount:{}]", contribAuth.getChainTaskId(), getWaitingTransactionCount());
                return sendContributeTransaction(contribAuth, resultHash, resultSeal, enclaveSignatureData);
            }, executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private IexecHubABILegacy.TaskContributeEventResponse sendContributeTransaction(ContributionAuthorization contribAuth, String resultHash, String resultSeal, Sign.SignatureData enclaveSignatureData) {
        IexecHubABILegacy.TaskContributeEventResponse contributeEvent = null;

        try {

            RemoteCall<TransactionReceipt> contributeCall = iexecHub.contributeABILegacy(
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
            if (!iexecHub.getTaskContributeEvents(contributeReceipt).isEmpty()) {
                log.info("Contributed [chainTaskId:{}, resultHash:{}, gasUsed:{}]", contribAuth.getChainTaskId(), resultHash, contributeReceipt.getGasUsed());
                contributeEvent = iexecHub.getTaskContributeEvents(contributeReceipt).get(0);
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
            RemoteCall<TransactionReceipt> revealCall = iexecHub.reveal(
                    stringToBytes(chainTaskId),
                    stringToBytes(resultDigest));
            log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);
            TransactionReceipt revealReceipt = revealCall.send();
            if (!iexecHub.getTaskRevealEvents(revealReceipt).isEmpty()) {
                log.info("Revealed [chainTaskId:{}, resultDigest:{}, gasUsed:{}]", chainTaskId, resultDigest, revealReceipt.getGasUsed());
                revealEvent = iexecHub.getTaskRevealEvents(revealReceipt).get(0);
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

    public Optional<ChainDeal> getChainDeal(String chainDealId) {
        return ChainUtils.getChainDeal(credentialsService.getCredentials(), web3j, iexecHub.getContractAddress(), chainDealId);
    }

    public Optional<ChainTask> getChainTask(String chainTaskId) {
        return ChainUtils.getChainTask(iexecHub, chainTaskId);
    }


    Optional<ChainAccount> getChainAccount() {
        return ChainUtils.getChainAccount(iexecClerk, credentialsService.getCredentials().getAddress());
    }

    Optional<ChainContribution> getChainContribution(String chainTaskId) {
        return ChainUtils.getChainContribution(iexecHub, chainTaskId, credentialsService.getCredentials().getAddress());
    }

    public Optional<ChainCategory> getChainCategory(long id) {
        return ChainUtils.getChainCategory(iexecHub, id);
    }

    public Optional<ChainApp> getChainApp(String address) {
        App app = ChainUtils.loadDappContract(credentialsService.getCredentials(), web3j, address);
        return ChainUtils.getChainApp(app);
    }

    public boolean hasEnoughGas() {
        return ChainUtils.hasEnoughGas(web3j, credentialsService.getCredentials().getAddress());
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
