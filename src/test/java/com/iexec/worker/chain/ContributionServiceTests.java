/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
            .contributionDeadline(new Date().getTime() + 10000)
            .contributors(List.of())
            .build();

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        contributionService = new ContributionService(iexecHubService, workerpoolAuthorizationService, enclaveAuthorizationService, WORKER_WALLET_ADDRESS);
    }

    @Test
    void shouldChainTaskBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().build());

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isTrue();
    }

    @Test
    void shouldChainTaskNotBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(null);

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isFalse();
    }

    //region getCannotContributeStatusCause
    @Test
    void getCannotContributeStatusShouldReturnChainUnreachable() {
        String chainTaskId = "chainTaskId";

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CHAIN_UNREACHABLE);

        verify(iexecHubService).getChainTask(chainTaskId);
        verifyNoInteractions(workerpoolAuthorizationService);
    }

    @Test
    void getCannotContributeStatusShouldReturnStakeTooLow() {
        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(STAKE_TOO_LOW);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verifyNoInteractions(workerpoolAuthorizationService);
    }

    @Test
    void getCannotContributeStatusShouldReturnTaskNotActive() {
        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(false);

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TASK_NOT_ACTIVE);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(iexecHubService).isChainTaskActive(chainTaskId);
        verifyNoInteractions(workerpoolAuthorizationService);
    }

    @Test
    void getCannotContributeStatusShouldReturnAfterDeadline() {
        ChainTask chainTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .contributionDeadline(new Date().getTime() - 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CONTRIBUTION_TIMEOUT);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(iexecHubService).isChainTaskActive(chainTaskId);
        verifyNoInteractions(workerpoolAuthorizationService);
    }

    @Test
    void getCannotContributeStatusShouldReturnContributionAlreadySet() {
        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CONTRIBUTION_ALREADY_SET);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(iexecHubService).isChainTaskActive(chainTaskId);
        verify(iexecHubService).getChainContribution(chainTaskId);
        verifyNoInteractions(workerpoolAuthorizationService);
    }

    @Test
    void getCannotContributeStatusCauseShouldReturnWorkerpoolAuthorizationNotFound() {
        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                        .status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(WORKERPOOL_AUTHORIZATION_NOT_FOUND);

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(iexecHubService).isChainTaskActive(chainTaskId);
        verify(iexecHubService).getChainContribution(chainTaskId);
        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
    }

    @Test
    void getCannotContributeStatusCauseShouldReturnEmpty() {
        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId))
                .thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                        .status(ChainContributionStatus.UNSET).build()));
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId))
                .thenReturn(new WorkerpoolAuthorization());

        assertThat(contributionService.getCannotContributeStatusCause(chainTaskId)).isEmpty();

        verify(iexecHubService).getChainTask(chainTaskId);
        verify(iexecHubService).getChainAccount();
        verify(iexecHubService).getChainDeal(CHAIN_DEAL_ID);
        verify(iexecHubService).isChainTaskActive(chainTaskId);
        verify(iexecHubService).getChainContribution(chainTaskId);
        verify(workerpoolAuthorizationService).getWorkerpoolAuthorization(chainTaskId);
    }
    //endregion

    // region getCannotContributeAndFinalizeStatusCause
    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnChainUnreachable() {
        String chainTaskId = "chainTaskId";

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(CHAIN_UNREACHABLE);

        verify(iexecHubService).getChainTask(chainTaskId);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTrustNotOne() {
        String chainTaskId = chainTask.getChainTaskId();

        ChainDeal chainDeal = ChainDeal.builder()
                .chainDealId(CHAIN_DEAL_ID)
                .trust(BigInteger.TWO)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(chainDeal));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TRUST_NOT_1);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnTaskAlreadyContributed() {
        ChainTask chainTask = ChainTask.builder()
                .dealid(CHAIN_DEAL_ID)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .contributors(List.of("CONTRIBUTED"))
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        ChainDeal chainDeal = ChainDeal.builder()
                .chainDealId(CHAIN_DEAL_ID)
                .trust(BigInteger.ONE)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(chainDeal));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId).orElse(null))
                .isEqualTo(TASK_ALREADY_CONTRIBUTED);
    }

    @Test
    void getCannotContributeAndFinalizeStatusCauseShouldReturnEmpty() {
        String chainTaskId = chainTask.getChainTaskId();

        ChainDeal chainDeal = ChainDeal.builder()
                .chainDealId(CHAIN_DEAL_ID)
                .trust(BigInteger.ONE)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(chainDeal));

        assertThat(contributionService.getCannotContributeAndFinalizeStatusCause(chainTaskId)).isEmpty();
    }
    // endregion

    @Test
    void getContribution() {
        String chainTaskId = "0x0000000000000000000000000000000000000000000000000000000000000001";
        String resultDigest = "0x0000000000000000000000000000000000000000000000000000000000000002";

        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

        WorkerpoolAuthorization teeWorkerpoolAuth = TestUtils.getTeeWorkerpoolAuth();
        when(workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId)).thenReturn(teeWorkerpoolAuth);
        when(iexecHubService.isTeeTask(chainTaskId)).thenReturn(false);

        ComputedFile computedFile = ComputedFile.builder()
                .taskId(chainTaskId)
                .resultDigest(resultDigest)
                .build();
        Contribution contribution = contributionService.getContribution(computedFile);

        System.out.println(contribution);

        Assertions.assertNotNull(contribution);

        Assertions.assertEquals(contribution,
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

        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(Credentials.create(TestUtils.WORKER_PRIVATE).getAddress(), chainTaskId, resultDigest);

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

        Assertions.assertNotNull(contribution);
        Assertions.assertEquals(chainTaskId, contribution.getChainTaskId());
        Assertions.assertEquals(resultDigest, contribution.getResultDigest());
        Assertions.assertEquals(resultSeal, contribution.getResultSeal());
        Assertions.assertEquals(TestUtils.ENCLAVE_ADDRESS, contribution.getEnclaveChallenge());
        Assertions.assertEquals("0xenclaveSignature", contribution.getEnclaveSignature());
        Assertions.assertEquals(teeWorkerpoolAuth.getSignature().getValue(), contribution.getWorkerPoolSignature());

        Contribution expectedContribution = Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(TestUtils.ENCLAVE_ADDRESS)
                .enclaveSignature("0xenclaveSignature")
                .workerPoolSignature(teeWorkerpoolAuth.getSignature().getValue())
                .build();
        Assertions.assertEquals(contribution, expectedContribution);

    }

}
