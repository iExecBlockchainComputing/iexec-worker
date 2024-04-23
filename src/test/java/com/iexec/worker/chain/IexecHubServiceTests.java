/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.contribution.Contribution;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.worker.config.ConfigServerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
class IexecHubServiceTests {

    private static final String TASK_CONTRIBUTE_NOTICE = Hash.sha3String("TaskContribute(bytes32,address,bytes32)");
    private static final String TASK_REVEAL_NOTICE = Hash.sha3String("TaskReveal(bytes32,address,bytes32)");
    private static final String TASK_FINALIZE_NOTICE = Hash.sha3String("TaskFinalize(bytes32,bytes)");
    private static final String CHAIN_TASK_ID = "0x5125c4ca7176e40d8c5386072a6f262029609a5d3a896fbf592cd965e65098d9";

    @Mock
    private ConfigServerConfigurationService configServerConfigurationService;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private Web3jService web3jService;
    @Mock
    private IexecHubContract iexecHubContract;
    @Mock
    private RemoteFunctionCall<TransactionReceipt> remoteFunctionCall;
    @Mock
    private Web3j web3jClient;
    private IexecHubService iexecHubService;
    private Credentials credentials;

    @BeforeEach
    void init() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(configServerConfigurationService.getIexecHubContractAddress()).thenReturn("0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860");
        when(configServerConfigurationService.getBlockTime()).thenReturn(Duration.ofSeconds(5L));
        when(configServerConfigurationService.getChainId()).thenReturn(65535);
        credentials = Credentials.create(Keys.createEcKeyPair());
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(web3jService.getWeb3j()).thenReturn(web3jClient);
        iexecHubService = spy(new IexecHubService(credentialsService, web3jService, configServerConfigurationService));
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", iexecHubContract);
    }

    private TransactionReceipt createReceiptWithoutLogs(List<Log> web3Logs) {
        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setBlockNumber("0x1");
        transactionReceipt.setGasUsed("0x186a0");
        transactionReceipt.setLogs(web3Logs);
        return transactionReceipt;
    }

    // region contribute
    @Test
    void shouldContribute() throws Exception {
        String workerAddress = Numeric.toHexStringNoPrefixZeroPadded(
                Numeric.toBigInt(credentials.getAddress()), 64);
        Log web3Log = new Log();
        web3Log.setData("0x1a538512b510ee384ce649b58a938d5c2df4ace50ef51d33f353276501e95662");
        web3Log.setTopics(List.of(TASK_CONTRIBUTE_NOTICE, CHAIN_TASK_ID, workerAddress));
        TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        when(iexecHubContract.contribute(any(), any(), any(), any(), any(), any())).thenReturn(remoteFunctionCall);
        when(remoteFunctionCall.send()).thenReturn(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultHash("resultHash")
                .resultSeal("resultSeal")
                .workerPoolSignature("workerPoolSignature")
                .build();
        IexecHubContract.TaskContributeEventResponse response = iexecHubService.contribute(contribution);
        assertThat(response).isNotNull();
    }

    @Test
    void shouldNotContributeOnExecutionException() throws ExecutionException, InterruptedException {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultHash("resultHash")
                .resultSeal("resultSeal")
                .workerPoolSignature("workerPoolSignature")
                .build();
        doThrow(ExecutionException.class).when(iexecHubService).submit(any());
        IexecHubContract.TaskContributeEventResponse response = iexecHubService.contribute(contribution);
        assertThat(response).isNull();
    }

    @Test
    void shouldNotContributeWhenInterrupted() throws ExecutionException, InterruptedException {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultHash("resultHash")
                .resultSeal("resultSeal")
                .workerPoolSignature("workerPoolSignature")
                .build();
        doThrow(InterruptedException.class).when(iexecHubService).submit(any());
        IexecHubContract.TaskContributeEventResponse response = iexecHubService.contribute(contribution);
        assertThat(response).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
    // endregion

    // region reveal
    @Test
    void shouldReveal() throws Exception {
        String workerAddress = Numeric.toHexStringNoPrefixZeroPadded(
                Numeric.toBigInt(credentials.getAddress()), 64);
        Log web3Log = new Log();
        web3Log.setData("0x88f79ce47dc9096bab83327fb3ae0cd99694fd36db6b5f22a4e4e7bf72e79989");
        web3Log.setTopics(List.of(TASK_REVEAL_NOTICE, CHAIN_TASK_ID, workerAddress));
        TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        when(iexecHubContract.reveal(any(), any())).thenReturn(remoteFunctionCall);
        when(remoteFunctionCall.send()).thenReturn(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        IexecHubContract.TaskRevealEventResponse response = iexecHubService.reveal(CHAIN_TASK_ID, "resultDigest");
        assertThat(response).isNotNull();
    }

    @Test
    void shouldNotRevealOnExecutionException() throws ExecutionException, InterruptedException {
        doThrow(ExecutionException.class).when(iexecHubService).submit(any());
        IexecHubContract.TaskRevealEventResponse response = iexecHubService.reveal(CHAIN_TASK_ID, "resultDigest");
        assertThat(response).isNull();
    }

    @Test
    void shouldNotRevealWhenInterrupted() throws ExecutionException, InterruptedException {
        doThrow(InterruptedException.class).when(iexecHubService).submit(any());
        IexecHubContract.TaskRevealEventResponse response = iexecHubService.reveal(CHAIN_TASK_ID, "resultDigest");
        assertThat(response).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
    // endregion

    // region contributeAndFinalize
    @Test
    void shouldContributeAndFinalize() throws Exception {
        Log web3Log = new Log();
        web3Log.setData("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000597b202273746f72616765223a202269706673222c20226c6f636174696f6e223a20222f697066732f516d6435763668723848385642444644746f777332786978466f76314833576f704a317645707758756d5a37325522207d00000000000000");
        web3Log.setTopics(List.of(TASK_FINALIZE_NOTICE, CHAIN_TASK_ID));
        TransactionReceipt transactionReceipt = createReceiptWithoutLogs(List.of(web3Log));
        when(iexecHubContract.contributeAndFinalize(any(), any(), any(), any(), any(), any(), any())).thenReturn(remoteFunctionCall);
        when(remoteFunctionCall.send()).thenReturn(transactionReceipt);
        doReturn(true).when(iexecHubService).isSuccessTx(any(), any(), any());

        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultDigest("resultDigest")
                .workerPoolSignature("workerPoolSignature")
                .build();
        Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "callbackData");
        assertThat(chainReceipt).isNotEmpty();
    }

    @Test
    void shouldNotContributeAndFinalizeOnExecutionException() throws Exception {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultDigest("resultDigest")
                .workerPoolSignature("workerPoolSignature")
                .build();
        doThrow(ExecutionException.class).when(iexecHubService).submit(any());
        Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "callbackData");
        assertThat(chainReceipt).isEmpty();
    }

    @Test
    void shouldNotContributeAndFinalizeWhenInterrupted() throws Exception {
        final Contribution contribution = Contribution.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge("enclaveChallenge")
                .enclaveSignature("enclaveSignature")
                .resultDigest("resultDigest")
                .workerPoolSignature("workerPoolSignature")
                .build();
        doThrow(InterruptedException.class).when(iexecHubService).submit(any());
        Optional<ChainReceipt> chainReceipt = iexecHubService.contributeAndFinalize(contribution, "resultLink", "callbackData");
        assertThat(chainReceipt).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
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

    // region ChainTask status
    @ParameterizedTest
    @EnumSource(value = ChainTaskStatus.class, names = "ACTIVE")
    void shouldChainTaskBeActive(ChainTaskStatus chainTaskStatus) {
        doReturn(Optional.of(ChainTask.builder().status(chainTaskStatus).build()))
                .when(iexecHubService).getChainTask(CHAIN_TASK_ID);
        assertThat(iexecHubService.isChainTaskActive(CHAIN_TASK_ID)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChainTaskStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void shouldChainTaskNotBeActive(ChainTaskStatus chainTaskStatus) {
        doReturn(Optional.of(ChainTask.builder().status(chainTaskStatus).build()))
                .when(iexecHubService).getChainTask(CHAIN_TASK_ID);
        assertThat(iexecHubService.isChainTaskActive(CHAIN_TASK_ID)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ChainTaskStatus.class, names = "REVEALING")
    void shouldChainTaskBeRevealing(ChainTaskStatus chainTaskStatus) {
        doReturn(Optional.of(ChainTask.builder().status(chainTaskStatus).build()))
                .when(iexecHubService).getChainTask(CHAIN_TASK_ID);
        assertThat(iexecHubService.isChainTaskRevealing(CHAIN_TASK_ID)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChainTaskStatus.class, names = "REVEALING", mode = EnumSource.Mode.EXCLUDE)
    void shouldChainTaskNotBeRevealing(ChainTaskStatus chainTaskStatus) {
        doReturn(Optional.of(ChainTask.builder().status(chainTaskStatus).build()))
                .when(iexecHubService).getChainTask(CHAIN_TASK_ID);
        assertThat(iexecHubService.isChainTaskRevealing(CHAIN_TASK_ID)).isFalse();
    }
    // endregion
}
