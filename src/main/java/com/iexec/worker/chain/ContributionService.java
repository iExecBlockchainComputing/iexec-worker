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
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;


@Slf4j
@Service
public class ContributionService {

    private final IexecHubService iexecHubService;
    private final WorkerpoolAuthorizationService workerpoolAuthorizationService;
    private final EnclaveAuthorizationService enclaveAuthorizationService;
    private final CredentialsService credentialsService;

    public ContributionService(IexecHubService iexecHubService,
                               WorkerpoolAuthorizationService workerpoolAuthorizationService,
                               EnclaveAuthorizationService enclaveAuthorizationService,
                               CredentialsService credentialsService) {
        this.iexecHubService = iexecHubService;
        this.workerpoolAuthorizationService = workerpoolAuthorizationService;
        this.enclaveAuthorizationService = enclaveAuthorizationService;
        this.credentialsService = credentialsService;
    }

    public boolean isChainTaskInitialized(String chainTaskId) {
        return iexecHubService.getTaskDescription(chainTaskId) != null;
    }

    public Optional<ReplicateStatusCause> getCannotContributeStatusCause(String chainTaskId) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (optionalChainTask.isEmpty()) {
            return Optional.of(CHAIN_UNREACHABLE);
        }

        ChainTask chainTask = optionalChainTask.get();

        if (!hasEnoughStakeToContribute(chainTask)) {
            return Optional.of(STAKE_TOO_LOW);
        }

        if (!isTaskActiveToContribute(chainTask)) {
            return Optional.of(TASK_NOT_ACTIVE);
        }

        if (!isBeforeContributionDeadlineToContribute(chainTask)) {
            return Optional.of(CONTRIBUTION_TIMEOUT);
        }

        if (!isContributionUnsetToContribute(chainTask)) {
            return Optional.of(CONTRIBUTION_ALREADY_SET);
        }

        if (!isWorkerpoolAuthorizationPresent(chainTaskId)) {
            return Optional.of(WORKERPOOL_AUTHORIZATION_NOT_FOUND);
        }

        return Optional.empty();
    }

    // TODO: trust could become part of TaskDescription to avoid fetching deal on-chain
    public Optional<ReplicateStatusCause> getCannotContributeAndFinalizeStatusCause(String chainTaskId) {
        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(chainTaskId);
        if (optionalChainTask.isEmpty()) {
            return Optional.of(CHAIN_UNREACHABLE);
        }
        ChainTask chainTask = optionalChainTask.get();

        // check TRUST is 1
        Optional<ChainDeal> oChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (oChainDeal.isEmpty() || !BigInteger.ONE.equals(oChainDeal.get().getTrust())) {
            return Optional.of(TRUST_NOT_1);
        }

        // check TASK_ALREADY_CONTRIBUTED
        if (!chainTask.getContributors().isEmpty()) {
            return Optional.of(TASK_ALREADY_CONTRIBUTED);
        }

        return Optional.empty();
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

    private boolean isTaskActiveToContribute(ChainTask chainTask) {
        return iexecHubService.isChainTaskActive(chainTask.getChainTaskId());
    }

    private boolean isBeforeContributionDeadlineToContribute(ChainTask chainTask) {
        return new Date().getTime() < chainTask.getContributionDeadline();
    }

    private boolean isContributionUnsetToContribute(ChainTask chainTask) {
        Optional<ChainContribution> optionalContribution = iexecHubService.getChainContribution(chainTask.getChainTaskId());
        if (optionalContribution.isEmpty()) return false;

        ChainContribution chainContribution = optionalContribution.get();
        return chainContribution.getStatus().equals(ChainContributionStatus.UNSET);
    }

    public boolean isContributionDeadlineReached(String chainTaskId) {
        Optional<ChainTask> oTask = iexecHubService.getChainTask(chainTaskId);

        return oTask.isEmpty() || !isBeforeContributionDeadlineToContribute(oTask.get());
    }

    // returns ChainReceipt of the contribution if successful, null otherwise
    public Optional<ChainReceipt> contribute(Contribution contribution) {

        IexecHubContract.TaskContributeEventResponse contributeResponse = iexecHubService.contribute(contribution);

        if (contributeResponse == null) {
            log.error("ContributeTransactionReceipt received but was null [chainTaskId:{}]", contribution.getChainTaskId());
            return Optional.empty();
        }

        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(contributeResponse.log, contribution.getChainTaskId(),
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
        String resultHash = ResultUtils.computeResultHash(chainTaskId, resultDigest);
        String resultSeal = ResultUtils.computeResultSeal(credentialsService.getCredentials().getAddress(), chainTaskId, resultDigest);
        String workerpoolSignature = workerpoolAuthorization.getSignature().getValue();
        String enclaveChallenge = workerpoolAuthorization.getEnclaveChallenge();
        String enclaveSignature = computedFile.getEnclaveSignature();

        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);
        if (isTeeTask) {
            if (!enclaveAuthorizationService.isVerifiedEnclaveSignature(chainTaskId,
                    resultHash, resultSeal, enclaveSignature, enclaveChallenge)) {
                log.error("Cannot get contribution with invalid enclave " +
                                "signature [chainTaskId:{}, resultHash:{}, " +
                                "resultSeal:{}, enclaveSignature:{}, " +
                                "enclaveChallenge:{}]", chainTaskId, resultHash,
                        resultSeal, enclaveSignature, enclaveChallenge);
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
