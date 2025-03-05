/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.commons.poco.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTests {

    private static final String CHAIN_DEAL_ID = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";
    private static final String WORKER_WALLET_ADDRESS = "0x49713c374C0D5259A0c0c4fCCd1254CdFd631b80";

    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private WorkerpoolAuthorizationService workerpoolAuthorizationService;
    @Mock
    private EnclaveAuthorizationService enclaveAuthorizationService;

    private ContributionService contributionService;

    private final ChainTask chainTask = ChainTask.builder()
            .dealid(CHAIN_DEAL_ID)
            .idx(0)
            .status(ChainTaskStatus.ACTIVE)
            .contributionDeadline(Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli())
            .contributors(List.of())
            .build();

    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(chainTask.getChainTaskId())
            .trust(BigInteger.ONE)
            .build();

    @BeforeEach
    void beforeEach() {
        contributionService = new ContributionService(iexecHubService, workerpoolAuthorizationService, enclaveAuthorizationService, WORKER_WALLET_ADDRESS);
    }

    @Test
    void shouldChainTaskBeInitialized() {
        final String chainTaskId = "0xabc";
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().build());

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isTrue();
    }

    @Test
    void shouldChainTaskNotBeInitialized() {
        final String chainTaskId = "0xabc";
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(null);

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isFalse();
    }

    //region getCannotContributeStatusCause
    @Test
    void getCannotContributeStatusCauseShouldReturnWorkerpoolAuthorizationNotFound() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(null);

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(WORKERPOOL_AUTHORIZATION_NOT_FOUND);

        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
    }

    @Test
    void getCannotContributeStatusShouldReturnChainUnreachable() {
        final String chainTaskId = "chainTaskId";

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CHAIN_UNREACHABLE);

        verify(iexecHubService).getChainTask(chainTaskId);
    }

    @Test
    void getCannotContributeStatusShouldReturnStakeTooLow() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(STAKE_TOO_LOW);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusShouldReturnTaskNotActive() {
        final ChainTask inactiveTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .status(ChainTaskStatus.UNSET)
                .build();
        final String chainTaskId = inactiveTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(inactiveTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TASK_NOT_ACTIVE);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusShouldReturnAfterDeadline() {
        final ChainTask timedOutChainTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .status(ChainTaskStatus.ACTIVE)
                .contributionDeadline(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli())
                .build();
        final String chainTaskId = timedOutChainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(timedOutChainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CONTRIBUTION_TIMEOUT);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusShouldReturnContributionAlreadySet() {
        final ChainTask alreadyContributedChainTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .status(ChainTaskStatus.ACTIVE)
                .contributionDeadline(Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli())
                .contributors(List.of(WORKER_WALLET_ADDRESS))
                .build();
        final String chainTaskId = alreadyContributedChainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(alreadyContributedChainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CONTRIBUTION_ALREADY_SET);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusCauseShouldReturnEmpty() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());
        when(iexecHubService.getChainTask(chainTaskId))
                .thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId)).isEmpty();

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
    }
    //endregion

    // region getCannotContributeAndFinalizeStatusCause
    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTrustNotOne() {
        final String chainTaskId = chainTask.getChainTaskId();
        final TaskDescription badTrustTaskDescription = TaskDescription.builder()
                .chainTaskId(chainTaskId)
                .trust(BigInteger.TWO)
                .build();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(badTrustTaskDescription);

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TRUST_NOT_1);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnChainUnreachable() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CHAIN_UNREACHABLE);

        verify(iexecHubService).getChainTask(chainTaskId);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTaskAlreadyContributed() {
        final ChainTask chainTaskWithContribution = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .contributionDeadline(Instant.now().plus(1, ChronoUnit.SECONDS).toEpochMilli())
                .contributors(List.of("CONTRIBUTED"))
                .build();

        final String chainTaskId = chainTaskWithContribution.getChainTaskId();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTaskWithContribution));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TASK_ALREADY_CONTRIBUTED);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnEmpty() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId)).isEmpty();
    }
    // endregion

    @Test
    void getContribution() {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000002";

        String resultHash = HashUtils.concatenateAndHash(chainTaskId, resultDigest);
        String resultSeal = HashUtils.concatenateAndHash(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        WorkerpoolAuthorization teeWorkerpoolAuth = TestUtils.getTeeWorkerpoolAuth();
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(false);

        ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .build();
        Contribution contribution = contributionService.getContribution(computedFile);

        assertNotNull(contribution);

        assertEquals(contribution,
                Contribution.builder()
                        .chainTaskId(chainTaskId)
                        .resultDigest(resultDigest)
                        .resultHash(resultHash)
                        .resultSeal(resultSeal)
                        .enclaveChallenge(teeWorkerpoolAuth.getEnclaveChallenge())
                        .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                        .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                        .build()
        );
    }

    @Test
    void getContributionWithTee() {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000002";
        String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000001";

        String resultHash = HashUtils.concatenateAndHash(chainTaskId, resultDigest);
        String resultSeal = HashUtils.concatenateAndHash(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        WorkerpoolAuthorization teeWorkerpoolAuth = TestUtils.getTeeWorkerpoolAuth();
        teeWorkerpoolAuth.setEnclaveChallenge(TestUtils.ENCLAVE_ADDRESS);
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(enclaveAuthorizationService.
                isVerifiedEnclaveSignature(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(true);

        ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .enclaveSignature("0xenclaveSignature")
                .build();
        Contribution contribution = contributionService.getContribution(computedFile);

        assertNotNull(contribution);
        assertEquals(chainTaskId, contribution.getChainTaskId());
        assertEquals(resultDigest, contribution.getResultDigest());
        assertEquals(resultSeal, contribution.getResultSeal());
        assertEquals(TestUtils.ENCLAVE_ADDRESS, contribution.getEnclaveChallenge());
        assertEquals("0xenclaveSignature", contribution.getEnclaveSignature());
        assertEquals(teeWorkerpoolAuth.getSignature().getValue(), contribution.getWorkerPoolSignature());

        Contribution expectedContribution = Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(TestUtils.ENCLAVE_ADDRESS)
                .enclaveSignature("0xenclaveSignature")
                .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                .build();
        assertEquals(contribution, expectedContribution);

    }

}
