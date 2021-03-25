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

import com.iexec.common.chain.*;
import com.iexec.common.contribution.Contribution;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.TestUtils;
import com.iexec.common.worker.result.ResultUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ContributionServiceTests {

    @Mock private IexecHubService iexecHubService;
    @Mock private WorkerpoolAuthorizationService workerpoolAuthorizationService;
    @Mock private EnclaveAuthorizationService enclaveAuthorizationService;
    @Mock private CredentialsService credentialsService;

    @InjectMocks
    private ContributionService contributionService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldChainTaskBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(new ChainTask()));

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isTrue();
    }

    @Test
    public void shouldChainTaskNotBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isFalse();
    }

    /**
     *  getCannotContributeStatusCause()
     */

    @Test
    public void getCannotContributeStatusShouldReturnStatusSinceChainTaskMissing() {
        String chainTaskId = "chainTaskId";

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).get())
                .isEqualTo(CHAIN_UNREACHABLE);
    }

    @Test
    public void getCannotContributeStatusShouldReturnStakeTooLoww() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).get())
                .isEqualTo(STAKE_TOO_LOW);
    }

    @Test
    public void getCannotContributeStatusShouldReturnTaskNotActive() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(false);

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).get())
                .isEqualTo(TASK_NOT_ACTIVE);
    }

    @Test
    public void getCannotContributeStatusShouldReturnAfterDeadline() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() - 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).get())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void getCannotContributeStatusShouldReturnContributionAlreadySet() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                .status(ChainContributionStatus.CONTRIBUTED).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).get()).isEqualTo(CONTRIBUTION_ALREADY_SET);
    }

    @Test
    public void getCannotContributeStatusShouldReturnEmpty() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId))
                .thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                .status(ChainContributionStatus.UNSET).build()));
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).isEmpty()).isTrue();
    }

    @Test
    public void getContribution() {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000002";

        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        WorkerpoolAuthorization teeWorkerpoolAuth = TestUtils.getTeeWorkerpoolAuth();
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(credentialsService.getCredentials()).thenReturn(Credentials.create(TestUtils.WORKER_PRIVATE));
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(false);

        ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .build();
        Contribution contribution = contributionService.getContribution(computedFile);

        System.out.println(contribution);

        Assert.assertNotNull(contribution);

        Assert.assertEquals(contribution,
                Contribution.builder()
                        .chainTaskId(chainTaskId)
                        .resultDigest(resultDigest)
                        .resultHash(resultHash)
                        .resultSeal(resultSeal)
                        .enclaveChallenge(teeWorkerpoolAuth.getEnclaveChallenge())
                        .enclaveSignature(BytesUtils.EMPTY_HEXASTRING_64)
                        .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                        .build()
        );
    }

    @Test
    public void getContributionWithTee() {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000001";

        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        WorkerpoolAuthorization teeWorkerpoolAuth = TestUtils.getTeeWorkerpoolAuth();
        teeWorkerpoolAuth.setEnclaveChallenge(TestUtils.ENCLAVE_ADDRESS);
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(enclaveAuthorizationService.
                isVerifiedEnclaveSignature(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(credentialsService.getCredentials()).thenReturn(Credentials.create(TestUtils.WORKER_PRIVATE));
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(true);

        ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .enclaveSignature("0xenclaveSignature")
                .build();
        Contribution contribution = contributionService.getContribution(computedFile);

        Assert.assertNotNull(contribution);
        Assert.assertEquals(contribution.getChainTaskId(), chainTaskId);
        Assert.assertEquals(contribution.getResultDigest(), resultDigest);
        Assert.assertEquals(contribution.getResultSeal(), resultSeal);
        Assert.assertEquals(contribution.getEnclaveChallenge(), TestUtils.ENCLAVE_ADDRESS);
        Assert.assertEquals("0xenclaveSignature", contribution.getEnclaveSignature());
        Assert.assertEquals(contribution.getWorkerPoolSignature(), teeWorkerpoolAuth.getSignature().getValue());

        Contribution expectedContribution = Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(TestUtils.ENCLAVE_ADDRESS)
                .enclaveSignature("0xenclaveSignature")
                .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                .build();
        Assert.assertEquals(contribution, expectedContribution);

    }

}
