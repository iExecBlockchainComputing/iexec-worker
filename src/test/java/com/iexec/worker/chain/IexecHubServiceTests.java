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

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.worker.config.BlockchainAdapterConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
class IexecHubServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    @Mock
    private BlockchainAdapterConfigurationService blockchainAdapterConfigurationService;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private Web3jService web3jService;
    @Mock
    private IexecHubContract iexecHubContract;
    @Mock
    private RemoteFunctionCall<TransactionReceipt> remoteCall;
    @Mock Web3j web3jClient;
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
    }

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
