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

package com.iexec.worker.compute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.iexec.common.replicate.ReplicateStatusCause;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ComputeExitCauseService {

    /**
     * @deprecated Use {@link #bulkExitCauseMap} instead
     */
    @Deprecated(since = "v9.0.1", forRemoval = true)
    private final HashMap<String, ReplicateStatusCause> exitCauseMap = new HashMap<>();

    private final Map<String, List<ReplicateStatusCause>> bulkExitCauseMap = new HashMap<>();

    /**
     * Report failure exit cause from pre-compute or post-compute enclave.
     *
     * @param computeStage pre-compute or post-compute-stage label
     * @param chainTaskId  task ID
     * @param exitCause    root cause of the failure
     * @return true if exit cause is reported
     * @deprecated Use {@link #setBulkExitCausesForGivenComputeStage(ComputeStage, String, List)}
     *             for bulk exit cause reporting instead
     */
    @Deprecated(since = "v9.0.1", forRemoval = true)
    boolean setExitCause(ComputeStage computeStage,
                         String chainTaskId,
                         ReplicateStatusCause exitCause) {
        String key = buildKey(computeStage, chainTaskId);
        if (exitCauseMap.containsKey(key)) {
            log.info("Cannot set exit cause since already set " +
                            "[computeStage:{}, chainTaskId:{}, exitCause:{}]",
                    computeStage, chainTaskId, exitCause);
            return false;
        }
        exitCauseMap.put(key, exitCause);
        log.info("Added exit cause [computeStage:{}, chainTaskId:{}, exitCause:{}]",
                computeStage, chainTaskId, exitCause);
        return true;
    }

    /**
     * Get exit cause for pre-compute or post-compute enclave.
     *
     * @param chainTaskId task ID
     * @return exit cause
     * @deprecated Use {@link #getExitCauseAndPruneForGivenComputeStage(ComputeStage, String)} instead
     */
    @Deprecated(since = "v9.0.1", forRemoval = true)
    ReplicateStatusCause getReplicateStatusCause(ComputeStage computeStage,
                                                         String chainTaskId) {
        return exitCauseMap.get(buildKey(computeStage, chainTaskId));
    }

    /**
     * Get pre-compute exit cause.
     *
     * @param chainTaskId task ID
     * @return exit cause
     * @deprecated Use {@link #getExitCauseAndPruneForGivenComputeStage(ComputeStage, String)}
     *             with ComputeStage.PRE instead
     */
    @Deprecated(since = "v9.0.1", forRemoval = true)
    public ReplicateStatusCause getPreComputeExitCauseAndPrune(String chainTaskId) {
        ComputeStage stage = ComputeStage.PRE;
        ReplicateStatusCause cause = getReplicateStatusCause(stage, chainTaskId);
        pruneExitCause(stage, chainTaskId);
        return cause != null ? cause : ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE;
    }

    /**
     * Get post-compute exit cause.
     *
     * @param chainTaskId task ID
     * @return exit cause
     * @deprecated Use {@link #getExitCauseAndPruneForGivenComputeStage(ComputeStage, String)}
     *             with ComputeStage.POST instead
     */
    @Deprecated(since = "v9.0.1", forRemoval = true)
    public ReplicateStatusCause getPostComputeExitCauseAndPrune(String chainTaskId) {
        ComputeStage stage = ComputeStage.POST;
        ReplicateStatusCause cause = getReplicateStatusCause(stage, chainTaskId);
        pruneExitCause(stage, chainTaskId);
        return cause != null ? cause : ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
    }

    /**
     * Build key for storing or retrieving an exit cause
     *
     * @param prefix      compute stage prefix
     * @param chainTaskId task ID
     * @return exit cause storage key
     */
    private String buildKey(ComputeStage prefix, String chainTaskId) {
        return prefix + "_" + chainTaskId;
    }

    /**
     * Prune exit cause.
     *
     * @param computeStage compute stage
     * @param chainTaskId  task ID
     */
    private void pruneExitCause(ComputeStage computeStage, String chainTaskId) {
        exitCauseMap.remove(buildKey(computeStage, chainTaskId));
    }

    /**
     * Store bulk exit causes for a specific compute stage.
     * If causes already exist for this compute stage and task, the new causes will be added to the existing list.
     *
     * @param computeStage compute stage
     * @param chainTaskId  task ID
     * @param causes       list of exit causes
     * @return true if causes were stored successfully
     */
    public boolean setBulkExitCausesForGivenComputeStage(ComputeStage computeStage, String chainTaskId, List<ReplicateStatusCause> causes) {
        if (causes == null || causes.isEmpty()) {
            log.error("Cannot set bulk exit causes with null or empty list [computeStage:{}, chainTaskId:{}]",
                    computeStage, chainTaskId);
            return false;
        }

        final String key = buildKey(computeStage, chainTaskId);
        bulkExitCauseMap.compute(key, (k, existingCauses) -> {
            if (existingCauses == null) {
                log.info("Added bulk exit causes [computeStage:{}, chainTaskId:{}, causeCount:{}]",
                        computeStage, chainTaskId, causes.size());
                return List.copyOf(causes);
            } else {
                log.info("Appended bulk exit causes to existing list [computeStage:{}, chainTaskId:{}, newCauseCount:{}, totalCauseCount:{}]",
                        computeStage, chainTaskId, causes.size(), existingCauses.size() + causes.size());
                final List<ReplicateStatusCause> combinedCauses = new ArrayList<>(existingCauses);
                combinedCauses.addAll(causes);
                return combinedCauses;
            }
        });
        return true;
    }

    /**
     * Get bulk exit causes for a specific compute stage and prune them.
     *
     * @param computeStage compute stage
     * @param chainTaskId  task ID
     * @return list of exit causes, or null if not found
     */
    public List<ReplicateStatusCause> getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage computeStage, String chainTaskId) {
        final String key = buildKey(computeStage, chainTaskId);
        final List<ReplicateStatusCause> causes = bulkExitCauseMap.remove(key);
        if (causes != null) {
            log.debug("Retrieved and pruned bulk exit causes [computeStage:{}, chainTaskId:{}, causeCount:{}]",
                    computeStage, chainTaskId, causes.size());
        }
        return causes;
    }
}
