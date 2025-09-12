/*
 * Copyright 2023-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.chain;

import com.iexec.commons.poco.chain.ChainContribution;
import com.iexec.commons.poco.chain.ChainContributionStatus;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.SignerService;
import com.iexec.commons.poco.encoding.LogTopic;
import com.iexec.worker.config.ConfigServerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class IexecHubServiceTests {

    private static final String CHAIN_TASK_ID = "0x5125c4ca7176e40d8c5386072a6f262029609a5d3a896fbf592cd965e65098d9";
    private static final String ENCLAVE_CHALLENGE = "0x123";
    private static final String RESULT_DIGEST = "0x456";
    private static final String RESULT_HASH = "0x789";
    private static final String RESULT_SEAL = "0xabc";
    private static final String CALLBACK_GAS_IN_HEX = "0x30D40"; // hexadecimal value for 200_000

    @Mock
    private ConfigServerConfigurationService configServerConfigurationService;
    @Mock
    private SignerService signerService;
    @Mock
    private PollingTransactionReceiptProcessor txReceiptProcessor;
    @Mock
    private RawTransactionManager txManager;
    @Mock
    private Web3jService web3jService;
    @Mock
    private Web3j web3jClient;
    private IexecHubService iexecHubService;
    private Credentials credentials;

    @BeforeEach
    void init() throws Exception {
        when(configServerConfigurationService.getIexecHubContractAddress()).thenReturn("0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860");
        credentials = Credentials.create(Keys.createEcKeyPair());
        when(signerService.getCredentials()).thenReturn(credentials);
        when(web3jService.getWeb3j()).thenReturn(web3jClient);
        iexecHubService = spy(new IexecHubService(signerService, web3jService, configServerConfigurationService));
        ReflectionTestUtils.setField(iexecHubService, "txReceiptProcessor", txReceiptProcessor);
        ReflectionTestUtils.setField(iexecHubService, "txManager", txManager);
    }

    private TransactionReceipt createReceiptWithoutLogs(List<Log> web3Logs) {
        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setBlockNumber("0x1");
        transactionReceipt.setGasUsed("0x186a0");
        transactionReceipt.setLogs(web3Logs);
        return transactionReceipt;
    }

    private void mockTransaction(final TransactionReceipt receipt) throws IOException, TransactionException {
        when(signerService.getNonce()).thenReturn(BigInteger.TEN);
        when(signerService.signAndSendTransaction(any(), any(), any(), any(), any())).thenReturn("txHash");
        when(txReceiptProcessor.waitForTransactionReceipt("txHash")).thenReturn(receipt);
    }

    // region contribute
    @Test
    void shouldContribute() throws IOException, TransactionException {
        final String workerAddress = Numeric.toHexStringNoPrefixZeroPadded(
                Numeric.toBigInt(credentials.getAddress()), 64);
        final Log web3Log = new Log();
        web3Log.setData("0x1a538512b510ee384ce649b58a938d5c2df4ace50ef51d33f353276501e95662");
        web3Log.setTopics(List.of(LogTopic.TASK_CONTRIBUTE_EVENT, CHAIN_TASK_ID, workerAddress));
        final TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        mockTransaction(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .enclaveSignature("enclaveSignature")
                .resultHash(RESULT_HASH)
                .resultSeal(RESULT_SEAL)
                .workerPoolSignature("workerPoolSignature")
                .build();
        final Log response = iexecHubService.contribute(contribution);
        assertThat(response).isNotNull();
    }

    @Test
    void shouldNotContributeOnIOException() throws IOException {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .enclaveSignature("enclaveSignature")
                .resultHash(RESULT_HASH)
                .resultSeal(RESULT_SEAL)
                .workerPoolSignature("workerPoolSignature")
                .build();
        when(signerService.signAndSendTransaction(any(), any(), any(), any(), any())).thenThrow(IOException.class);
        final Log response = iexecHubService.contribute(contribution);
        assertThat(response).isNull();
    }
    // endregion

    // region reveal
    @Test
    void shouldReveal() throws IOException, TransactionException {
        final String workerAddress = Numeric.toHexStringNoPrefixZeroPadded(
                Numeric.toBigInt(credentials.getAddress()), 64);
        final Log web3Log = new Log();
        web3Log.setData("0x88f79ce47dc9096bab83327fb3ae0cd99694fd36db6b5f22a4e4e7bf72e79989");
        web3Log.setTopics(List.of(LogTopic.TASK_REVEAL_EVENT, CHAIN_TASK_ID, workerAddress));
        final TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        mockTransaction(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Log response = iexecHubService.reveal(CHAIN_TASK_ID, RESULT_DIGEST);
        assertThat(response).isNotNull();
    }

    @Test
    void shouldNotRevealOnIOException() throws IOException {
        doThrow(IOException.class).when(signerService).signAndSendTransaction(any(), any(), any(), any(), any());
        final Log response = iexecHubService.reveal(CHAIN_TASK_ID, RESULT_DIGEST);
        assertThat(response).isNull();
    }
    // endregion

    // region contributeAndFinalize
    @Test
    void shouldContributeAndFinalizeWithCallback() throws IOException, TransactionException {
        final Log web3Log = new Log();
        web3Log.setData("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000597b202273746f72616765223a202269706673222c20226c6f636174696f6e223a20222f697066732f516d6435763668723848385642444644746f777332786978466f76314833576f704a317645707758756d5a37325522207d00000000000000");
        web3Log.setTopics(List.of(LogTopic.TASK_FINALIZE_EVENT, CHAIN_TASK_ID));
        final TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        when(signerService.estimateGas(any(), any())).thenReturn(BigInteger.valueOf(100_000L));
        when(txManager.sendCall(any(), any(), any())).thenReturn(CALLBACK_GAS_IN_HEX);
        mockTransaction(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .enclaveSignature("enclaveSignature")
                .resultDigest(RESULT_DIGEST)
                .workerPoolSignature("workerPoolSignature")
                .build();
        final Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "callbackData");
        assertThat(chainReceipt).isNotEmpty();
    }

    @Test
    void shouldContributeAndFinalizeWithoutCallback() throws IOException, TransactionException {
        final Log web3Log = new Log();
        web3Log.setData("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000597b202273746f72616765223a202269706673222c20226c6f636174696f6e223a20222f697066732f516d6435763668723848385642444644746f777332786978466f76314833576f704a317645707758756d5a37325522207d00000000000000");
        web3Log.setTopics(List.of(LogTopic.TASK_FINALIZE_EVENT, CHAIN_TASK_ID));
        final TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        when(signerService.estimateGas(any(), any())).thenReturn(BigInteger.valueOf(100_000L));
        when(txManager.sendCall(any(), any(), any())).thenReturn(CALLBACK_GAS_IN_HEX);
        mockTransaction(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .enclaveSignature("enclaveSignature")
                .resultDigest(RESULT_DIGEST)
                .workerPoolSignature("workerPoolSignature")
                .build();
        final Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "");
        assertThat(chainReceipt).isNotEmpty();
    }

    @Test
    void shouldNotContributeAndFinalizeOnIOException() throws IOException {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(ENCLAVE_CHALLENGE)
                .enclaveSignature("enclaveSignature")
                .resultDigest(RESULT_DIGEST)
                .workerPoolSignature("workerPoolSignature")
                .build();
        when(signerService.estimateGas(any(), any())).thenReturn(BigInteger.valueOf(100_000L));
        when(txManager.sendCall(any(), any(), any())).thenReturn(CALLBACK_GAS_IN_HEX);
        doThrow(IOException.class).when(signerService).signAndSendTransaction(any(), any(), any(), any(), any());
        final Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "");
        assertThat(chainReceipt).isEmpty();
    }
    // endregion

    // region isSuccessTx
    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class)
    void shouldTxBeSuccess(ChainContributionStatus chainContributionStatus) {
        Log log = new Log();
        log.setType("");
        assertThat(iexecHubService.isSuccessTx(CHAIN_TASK_ID, log, chainContributionStatus)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class)
    void shouldTxNotBeSuccessWhenLogIsNull(ChainContributionStatus chainContributionStatus) {
        assertThat(iexecHubService.isSuccessTx(CHAIN_TASK_ID, null, chainContributionStatus)).isFalse();
    }

    @Test
    void shouldTxNotBeSuccessWhenTimeout() {
        Log log = new Log();
        log.setType("pending");
        when(web3jService.getBlockTime()).thenReturn(Duration.ofMillis(100L));
        doReturn(Optional.empty()).when(iexecHubService).getChainContribution(CHAIN_TASK_ID);
        assertThat(iexecHubService.isSuccessTx(CHAIN_TASK_ID, log, ChainContributionStatus.CONTRIBUTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class)
    void test(ChainContributionStatus chainContributionStatus) {
        Log log = new Log();
        log.setType("pending");
        ChainContribution chainContribution = ChainContribution.builder().status(chainContributionStatus).build();
        when(web3jService.getBlockTime()).thenReturn(Duration.ofMillis(100L));
        doReturn(Optional.of(chainContribution)).when(iexecHubService).getChainContribution(CHAIN_TASK_ID);
        assertThat(iexecHubService.isSuccessTx(CHAIN_TASK_ID, log, chainContributionStatus)).isTrue();
    }
    // endregion
}
