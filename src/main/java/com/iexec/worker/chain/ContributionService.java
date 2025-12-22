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

import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.worker.workflow.WorkflowError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;

@Slf4j
@Service
public class ContributionService {

    private final IexecHubService iexecHubService;
    private final WorkerpoolAuthorizationService workerpoolAuthorizationService;
    private final EnclaveAuthorizationService enclaveAuthorizationService;
    private final String workerWalletAddress;

    public ContributionService(IexecHubService iexecHubService,
                               WorkerpoolAuthorizationService workerpoolAuthorizationService,
                               EnclaveAuthorizationService enclaveAuthorizationService,
                               String workerWalletAddress) {
        this.iexecHubService = iexecHubService;
        this.workerpoolAuthorizationService = workerpoolAuthorizationService;
        this.enclaveAuthorizationService = enclaveAuthorizationService;
        this.workerWalletAddress = workerWalletAddress;
    }

    public boolean isChainTaskInitialized(String chainTaskId) {
        return iexecHubService.getTaskDescription(chainTaskId) != null;
    }

    public List<WorkflowError> getCannotContributeStatusCause(final String chainTaskId) {
        final List<WorkflowError> errors = new ArrayList<>();

        if (!isWorkerpoolAuthorizationPresent(chainTaskId)) {
            errors.add(new WorkflowError(WORKERPOOL_AUTHORIZATION_NOT_FOUND));
        }

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        final ChainTask chainTask = iexecHubService.getChainTask(chainTaskId).orElse(null);
        if (chainTask == null) {
            errors.add(new WorkflowError(CHAIN_UNREACHABLE));
            return errors;
        }

        // No staking in contributeAndFinalize
        if (taskDescription != null && !taskDescription.isEligibleToContributeAndFinalize()
                && !hasEnoughStakeToContribute(chainTask)) {
            errors.add(new WorkflowError(STAKE_TOO_LOW));
        }

        if (chainTask.getStatus() != ChainTaskStatus.ACTIVE) {
            errors.add(new WorkflowError(TASK_NOT_ACTIVE));
        }

        if (chainTask.isContributionDeadlineReached()) {
            errors.add(new WorkflowError(CONTRIBUTION_TIMEOUT));
        }

        if (chainTask.hasContributionFrom(workerWalletAddress)) {
            errors.add(new WorkflowError(CONTRIBUTION_ALREADY_SET));
        }

        return errors;
    }

    public List<WorkflowError> getCannotContributeAndFinalizeStatusCause(final String chainTaskId) {
        final List<WorkflowError> errors = new ArrayList<>();

        // check TRUST is 1
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription == null || !BigInteger.ONE.equals(taskDescription.getTrust())) {
            errors.add(new WorkflowError(TRUST_NOT_1));
        }

        final ChainTask chainTask = iexecHubService.getChainTask(chainTaskId).orElse(null);
        if (chainTask == null) {
            errors.add(new WorkflowError(CHAIN_UNREACHABLE));
            return errors;
        }

        // check TASK_ALREADY_CONTRIBUTED
        if (chainTask.hasContributions()) {
            errors.add(new WorkflowError(TASK_ALREADY_CONTRIBUTED));
        }

        return errors;
    }

    private boolean isWorkerpoolAuthorizationPresent(String chainTaskId) {
        WorkerpoolAuthorization workerpoolAuthorization =
                workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId);
        if (workerpoolAuthorization != null) {
            return true;
        }
        log.error("WorkerpoolAuthorization missing [chainTaskId:{}]", chainTaskId);
        return false;
    }

    private boolean hasEnoughStakeToContribute(ChainTask chainTask) {
        Optional<ChainAccount> optionalChainAccount = iexecHubService.getChainAccount();
        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (optionalChainAccount.isEmpty() || optionalChainDeal.isEmpty()) {
            return false;
        }
        return optionalChainAccount.get().getDeposit() >= optionalChainDeal.get().getWorkerStake().longValue();
    }

    // returns ChainReceipt of the contribution if successful, null otherwise
    public Optional<ChainReceipt> contribute(final Contribution contribution) {

        final Log contributeResponse = iexecHubService.contribute(contribution);

        if (contributeResponse == null) {
            log.error("ContributeTransactionReceipt received but was null [chainTaskId:{}]", contribution.chainTaskId());
            return Optional.empty();
        }

        final ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(contributeResponse, contribution.chainTaskId(),
                iexecHubService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }

    public WorkerpoolAuthorization getWorkerpoolAuthorization(String chainTaskId) {
        return workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId);
    }

    public Contribution getContribution(ComputedFile computedFile) {
        String chainTaskId = computedFile.getTaskId();
        WorkerpoolAuthorization workerpoolAuthorization = workerpoolAuthorizationService.getWorkerpoolAuthorization(chainTaskId);
        if (workerpoolAuthorization == null) {
            log.error("Cant getContribution (cant getWorkerpoolAuthorization) [chainTaskId:{}]", chainTaskId);
            return null;
        }

        String resultDigest = computedFile.getResultDigest();
        String resultHash = HashUtils.concatenateAndHash(chainTaskId, resultDigest);
        String resultSeal = HashUtils.concatenateAndHash(workerWalletAddress, chainTaskId, resultDigest);
        String workerpoolSignature = workerpoolAuthorization.getSignature().getValue();
        String enclaveChallenge = workerpoolAuthorization.getEnclaveChallenge();
        String enclaveSignature = computedFile.getEnclaveSignature();

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        if (taskDescription.requiresSgx() || taskDescription.requiresTdx()) {
            if (!enclaveAuthorizationService.isVerifiedEnclaveSignature(
                    chainTaskId, resultHash, resultSeal, enclaveSignature, enclaveChallenge)) {
                log.error("Cannot get contribution with invalid enclave " +
                                "signature [chainTaskId:{}, resultHash:{}, " +
                                "resultSeal:{}, enclaveSignature:{}, enclaveChallenge:{}]",
                        chainTaskId, resultHash, resultSeal, enclaveSignature, enclaveChallenge);
                return null;
            }
        } else {
            enclaveSignature = BytesUtils.EMPTY_HEX_STRING_32;
        }

        return Contribution.builder()
                .chainTaskId(chainTaskId)
                .resultDigest(resultDigest)
                .resultHash(resultHash)
                .resultSeal(resultSeal)
                .enclaveChallenge(enclaveChallenge)
                .enclaveSignature(enclaveSignature)
                .workerPoolSignature(workerpoolSignature)
                .build();
    }

}
