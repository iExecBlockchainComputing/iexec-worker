package com.iexec.worker.chain;


import com.iexec.common.chain.*;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.contract.generated.App;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.feign.CustomFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.utils.BytesUtils.EMPTY_ADDRESS;
import static com.iexec.common.utils.BytesUtils.EMPTY_HEXASTRING_64;
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

    IexecHubABILegacy.TaskContributeEventResponse contribute(ContributionAuthorization contribAuth, String resultHash, String resultSeal, Signature executionEnclaveSignature) throws ExecutionException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requested  contribute [chainTaskId:{}, waitingTxCount:{}]", contribAuth.getChainTaskId(), getWaitingTransactionCount());
            return sendContributeTransaction(contribAuth, resultHash, resultSeal, executionEnclaveSignature);
        }, executor).get();
    }

    private IexecHubABILegacy.TaskContributeEventResponse sendContributeTransaction(ContributionAuthorization contribAuth, String resultHash, String resultSeal, Signature executionEnclaveSignature) {
        BigInteger enclaveSignV = BigInteger.ZERO;
        byte[] enclaveSignR = stringToBytes(EMPTY_HEXASTRING_64);
        byte[] enclaveSignS = stringToBytes(EMPTY_HEXASTRING_64);

        if (!(contribAuth.getEnclave().equals(EMPTY_ADDRESS) || contribAuth.getEnclave().isEmpty())){
            
            if (executionEnclaveSignature == null){
                log.info("executionEnclaveSignature should not be null, can't contribute [chainTaskId:{]", contribAuth.getChainTaskId());
            }

            byte[] vBytes = new byte[1];
            vBytes[0] = executionEnclaveSignature.getSignV();

            enclaveSignV = new BigInteger(vBytes);
            enclaveSignR = executionEnclaveSignature.getSignR();
            enclaveSignS = executionEnclaveSignature.getSignS();
       
        }

        // No SGX used for now
        IexecHubABILegacy.TaskContributeEventResponse contributeEvent = null;
        try {

            RemoteCall<TransactionReceipt> contributeCall = iexecHub.contributeABILegacy(
                    stringToBytes(contribAuth.getChainTaskId()),
                    stringToBytes(resultHash),
                    stringToBytes(resultSeal),
                    contribAuth.getEnclave(),
                    enclaveSignV,
                    enclaveSignR,
                    enclaveSignS,
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
                    stringToBytes(chainTaskId),
                    stringToBytes(resultDigest));
            log.info("Sent reveal [chainTaskId:{}, resultDigest:{}]", chainTaskId, resultDigest);
            TransactionReceipt revealReceipt = revealCall.send();
            if (!iexecHub.getTaskRevealEvents(revealReceipt).isEmpty()) {
                log.info("Revealed [chainTaskId:{}, resultDigest:{}, gasUsed:{}]", chainTaskId, resultDigest, revealReceipt.getGasUsed());
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


}
