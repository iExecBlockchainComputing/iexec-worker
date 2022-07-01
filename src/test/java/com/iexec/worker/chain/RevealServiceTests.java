/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.contract.generated.IexecHubContract;
import com.iexec.common.utils.HashUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RevealServiceTests {

    @Mock private IexecHubService iexecHubService;
    @Mock private CredentialsService credentialsService;
    @Mock private Web3jService web3jService;

    @InjectMocks
    private RevealService revealService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    // main test that should be valid, all other tests are failing cases of this one

    @Test
    void canRevealAllValid() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        // when(resultService.getDeterministHashForTask(chainTaskId)).thenReturn(determinismHash);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isTrue();
    }

    @Test
    void cannotRevealSinceChainTaskStatusWrong() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(false);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceRevealDeadlineReached() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), -1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceChainContributionStatusWrong() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), -1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.UNSET)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceHashDoesntMatchConsensus() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(Hash.sha3("different hash value"))
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceContributionResultHashWrong() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(Hash.sha3("Dummy contribution value"))
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceContributionResultSealWrong() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(Hash.sha3("Dummy contribution seal"))
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceCannotFindChainTask() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceCannotFindChainContribution() {
        String determinismHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, determinismHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.empty());
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);


        assertThat(revealService.canReveal(chainTaskId, determinismHash)).isFalse();
    }

    @Test
    void cannotRevealSinceDeterministHashIsEmpty() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(iexecHubService.isChainTaskRevealing(chainTaskId)).thenReturn(true);

        assertThat(revealService.canReveal(chainTaskId, "")).isFalse();
    }

    @Test
    void shouldNotRevealWithEmptyDeterministHash() throws Exception {
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        assertThat(revealService.reveal(chainTaskId, "")).isEqualTo(Optional.empty());
    }

    @Test
    void shouldTriggerExceptionIfTransactionFails() throws Exception {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

        when(iexecHubService.reveal(chainTaskId, deterministHash)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> revealService.reveal(chainTaskId, deterministHash));
    }

    @Test
    void shouldRevealIfTransactionSucceeds() throws Exception {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

        IexecHubContract.TaskRevealEventResponse response = new IexecHubContract.TaskRevealEventResponse();

        // 0x200 in hexa = 512 in decimal
        response.log =
                new Log(false, "logIndex", "transactionIndex", "transactionHash",
                        "blockHash", "0x200", "address", "data", "type", new ArrayList<>());

        when(iexecHubService.reveal(chainTaskId, deterministHash)).thenReturn(response);
        assertThat(revealService.reveal(chainTaskId, deterministHash).get().getBlockNumber()).isEqualTo(512);
    }

    @Test
    void shouldConsensusBlockNotBeReached() {
        String chainTaskId = "0xabc";
        long consensusBlock = 10;

        when(web3jService.isBlockAvailable(consensusBlock)).thenReturn(false);

        assertThat(revealService.isConsensusBlockReached(chainTaskId, consensusBlock)).isFalse();
    }

    @Test
    void shouldConsensusBlockBeReached() {
        String chainTaskId = "0xabc";
        long consensusBlock = 10;

        when(web3jService.isBlockAvailable(consensusBlock)).thenReturn(true);

        assertThat(revealService.isConsensusBlockReached(chainTaskId, consensusBlock)).isTrue();
    }
}
