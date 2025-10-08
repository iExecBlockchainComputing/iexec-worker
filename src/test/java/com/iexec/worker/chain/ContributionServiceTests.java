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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContributionServiceTests {

    private static final String CHAIN_DEAL_ID = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";
    private static final String WORKER_WALLET_ADDRESS = "0x49713c374C0D5259A0c0c4fCCd1254CdFd631b80";
    private static final String ENCLAVE_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String CHAIN_TASK_ID = "0x1111111111111111111111111111111111111111111111111111111111111111";
    private static final String POOL_PRIVATE = "0xe2a973b083fae8043543f15313955aecee9de809a318656c1cfb22d3a6d52de1";
    private static final String WORKER_PRIVATE = "0xd0db6df0ebcd1d41439d91d86c5fc5c1806ee9cd8e71e3d5544bb7294b435c26";

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
        final TaskDescription contributeAndFinalizeTaskDescription = TaskDescription.builder()
                .chainTaskId(chainTaskId)
                .trust(BigInteger.ONE)
                .isTeeTask(true)
                .build();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(null);
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(contributeAndFinalizeTaskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeStatusCause(chainTaskId);
        assertThat(causes).containsExactly(WORKERPOOL_AUTHORIZATION_NOT_FOUND);

        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
    }

    @Test
    void getCannotContributeStatusShouldReturnChainUnreachable() {
        final String chainTaskId = "chainTaskId";
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeStatusCause(chainTaskId);
        assertThat(causes).containsExactly(CHAIN_UNREACHABLE);

        verify(iexecHubService).getChainTask(chainTaskId);
    }

    @Test
    void getCannotContributeStatusShouldReturnStakeTooLow() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId))
                .containsExactly(STAKE_TOO_LOW);

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
                .contributionDeadline(Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli())
                .contributors(List.of())
                .build();
        final String chainTaskId = inactiveTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(inactiveTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId))
                .containsExactly(TASK_NOT_ACTIVE);

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
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(timedOutChainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId))
                .containsExactly(CONTRIBUTION_TIMEOUT);

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
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(alreadyContributedChainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId))
                .containsExactly(CONTRIBUTION_ALREADY_SET);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusCauseShouldReturnEmpty() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
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

    @Test
    void getCannotContributeStatusShouldReturnEmptyForContributeAndFinalizeFlow() {
        final String chainTaskId = chainTask.getChainTaskId();
        final TaskDescription contributeAndFinalizeTaskDescription = TaskDescription.builder()
                .trust(BigInteger.ONE)
                .isTeeTask(true)
                .build();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(contributeAndFinalizeTaskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId)).isEmpty();

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService, never()).getChainAccount();
        verify(iexecHubService, never()).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusShouldReturnMultipleErrors() {
        final ChainTask problematicChainTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .status(ChainTaskStatus.UNSET) // Not active
                .contributionDeadline(Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()) // Deadline reached
                .contributors(List.of(WORKER_WALLET_ADDRESS)) // Already contributed
                .build();
        final String chainTaskId = problematicChainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(getTeeWorkerpoolAuth());
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(problematicChainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(0).build())); // Also stake too low
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeStatusCause(chainTaskId);

        assertThat(causes).containsExactly(
                STAKE_TOO_LOW,
                TASK_NOT_ACTIVE,
                CONTRIBUTION_TIMEOUT,
                CONTRIBUTION_ALREADY_SET
        );

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
    }

    @Test
    void getCannotContributeStatusShouldReturnAuthAndChainUnreachableErrors() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(null);
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeStatusCause(chainTaskId);

        assertThat(causes).containsExactly(
                WORKERPOOL_AUTHORIZATION_NOT_FOUND,
                CHAIN_UNREACHABLE
        );

        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
        verify(iexecHubService).getChainTask(chainTaskId);
    }
    //endregion

    // region getCannotContributeAndFinalizeStatusCause
    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTrustNotOne() {
        final String chainTaskId = chainTask.getChainTaskId();
        final TaskDescription badTrustTaskDescription = TaskDescription.builder()
                .trust(BigInteger.TWO)
                .build();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(badTrustTaskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId);
        assertThat(causes).containsExactly(TRUST_NOT_1);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnChainUnreachable() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId))
                .containsExactly(CHAIN_UNREACHABLE);

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

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId))
                .containsExactly(TASK_ALREADY_CONTRIBUTED);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnEmpty() {
        final String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(taskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId)).isEmpty();
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnMultipleErrors() {
        final String chainTaskId = chainTask.getChainTaskId();
        final TaskDescription badTrustTaskDescription = TaskDescription.builder()
                .trust(BigInteger.valueOf(2))
                .build();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(badTrustTaskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId);

        assertThat(causes).containsExactly(
                TRUST_NOT_1,
                CHAIN_UNREACHABLE
        );

        verify(iexecHubService).getChainTask(chainTaskId);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTrustAndTaskAlreadyContributed() {
        final ChainTask chainTaskWithContribution = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .contributionDeadline(Instant.now().plus(1, ChronoUnit.SECONDS).toEpochMilli())
                .contributors(List.of("CONTRIBUTED"))
                .build();

        final String chainTaskId = chainTaskWithContribution.getChainTaskId();
        final TaskDescription badTrustTaskDescription = TaskDescription.builder()
                .trust(BigInteger.valueOf(2))
                .build();

        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(badTrustTaskDescription);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTaskWithContribution));

        List<ReplicateStatusCause> causes = contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId);

        assertThat(causes).containsExactly(
                TRUST_NOT_1,
                TASK_ALREADY_CONTRIBUTED
        );

        verify(iexecHubService).getChainTask(chainTaskId);
    }
    // endregion

    @Test
    void getContribution() {
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000002";

        final String resultHash = HashUtils.concatenateAndHash(chainTaskId, resultDigest);
        final String resultSeal = HashUtils.concatenateAndHash(Credentials.create(WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        final WorkerpoolAuthorization teeWorkerpoolAuth = getTeeWorkerpoolAuth();
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(false);

        final ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .build();
        final Contribution contribution = contributionService.getContribution(computedFile);

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
        final String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000002";
        final String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000001";

        final String resultHash = HashUtils.concatenateAndHash(chainTaskId, resultDigest);
        final String resultSeal = HashUtils.concatenateAndHash(Credentials.create(WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        final WorkerpoolAuthorization teeWorkerpoolAuth = getTeeWorkerpoolAuth();
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(enclaveAuthorizationService.
                isVerifiedEnclaveSignature(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(true);

        final ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .enclaveSignature("0xenclaveSignature")
                .build();
        final Contribution contribution = contributionService.getContribution(computedFile);

        assertNotNull(contribution);
        assertEquals(chainTaskId, contribution.chainTaskId());
        assertEquals(resultDigest, contribution.resultDigest());
        assertEquals(resultSeal, contribution.resultSeal());
        assertEquals(ENCLAVE_ADDRESS, contribution.enclaveChallenge());
        assertEquals("0xenclaveSignature", contribution.enclaveSignature());
        assertEquals(teeWorkerpoolAuth.getSignature().getValue(), contribution.workerPoolSignature());

        final Contribution expectedContribution = Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(ENCLAVE_ADDRESS)
                .enclaveSignature("0xenclaveSignature")
                .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                .build();
        assertEquals(contribution, expectedContribution);

    }

    private WorkerpoolAuthorization getTeeWorkerpoolAuth() {
        final String hash = HashUtils.concatenateAndHash(WORKER_WALLET_ADDRESS, CHAIN_TASK_ID, ENCLAVE_ADDRESS);
        final Signature signature = SignatureUtils.signMessageHashAndGetSignature(hash, POOL_PRIVATE);
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .workerWallet(WORKER_WALLET_ADDRESS)
                .enclaveChallenge(ENCLAVE_ADDRESS)
                .signature(signature)
                .build();
    }

}
