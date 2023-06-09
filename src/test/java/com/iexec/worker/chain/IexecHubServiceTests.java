/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.worker.config.BlockchainAdapterConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
class IexecHubServiceTests {

    private static final String TASK_FINALIZE_NOTICE = Hash.sha3String("TaskFinalize(bytes32,bytes)");
    private static final String CHAIN_TASK_ID = "0x5125c4ca7176e40d8c5386072a6f262029609a5d3a896fbf592cd965e65098d9";

    @Mock
    private BlockchainAdapterConfigurationService blockchainAdapterConfigurationService;
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

    @BeforeEach
    void init() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(blockchainAdapterConfigurationService.getIexecHubContractAddress()).thenReturn("hub");
        when(blockchainAdapterConfigurationService.getBlockTime()).thenReturn(Duration.ofSeconds(5L));
        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(65535);
        when(credentialsService.getCredentials()).thenReturn(Credentials.create(Keys.createEcKeyPair()));
        when(web3jService.getWeb3j()).thenReturn(web3jClient);
        try (MockedStatic<IexecHubContract> iexecHubContract = Mockito.mockStatic(IexecHubContract.class)) {
            final IexecHubContract mockIexecContract = mock(IexecHubContract.class);
            final RemoteFunctionCall<BigInteger> mockRemoteFunctionCall = mock(RemoteFunctionCall.class);
            iexecHubContract.when(() -> IexecHubContract.load(any(), any(), (TransactionManager) any(), any()))
                    .thenReturn(mockIexecContract);
            when(mockIexecContract.contribution_deadline_ratio()).thenReturn(mockRemoteFunctionCall);
            when(mockRemoteFunctionCall.send()).thenReturn(BigInteger.ONE);
            iexecHubService = spy(new IexecHubService(credentialsService, web3jService, blockchainAdapterConfigurationService));
        }
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", iexecHubContract);
    }

    // region contributeAndFinalize
    @Test
    void shouldContributeAndFinalize() throws Exception {
        Log web3Log = new Log();
        web3Log.setData("0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000597b202273746f72616765223a202269706673222c20226c6f636174696f6e223a20222f697066732f516d6435763668723848385642444644746f777332786978466f76314833576f704a317645707758756d5a37325522207d00000000000000");
        web3Log.setTopics(List.of(TASK_FINALIZE_NOTICE, CHAIN_TASK_ID));
        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setBlockNumber("0x1");
        transactionReceipt.setGasUsed("0x186a0");
        transactionReceipt.setLogs(List.of(web3Log));
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
