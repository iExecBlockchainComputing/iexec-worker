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

package com.iexec.worker.compute;

import com.iexec.common.replicate.ReplicateStatusCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;


@Slf4j
@Service
public class ComputeExitCauseService {

    private final HashMap<String, List<ReplicateStatusCause>> exitCauseMap = new HashMap<>();

    /**
     * Report failure exit causes from pre-compute or post-compute enclave.
     *
     * @param computeStage pre-compute or post-compute-stage label
     * @param chainTaskId  task ID
     * @param causes       list of root causes of the failure
     * @return true if exit causes are reported
     */
    boolean setExitCausesForGivenComputeStage(ComputeStage computeStage,
                                              String chainTaskId,
                                              List<ReplicateStatusCause> causes) {
        if (causes == null || causes.isEmpty()) {
            log.error("Cannot set exit causes with null or empty list [computeStage:{}, chainTaskId:{}]",
                    computeStage, chainTaskId);
            return false;
        }

        final String key = buildKey(computeStage, chainTaskId);
        exitCauseMap.compute(key, (k, existingCauses) -> {
            if (existingCauses == null) {
                log.info("Added exit causes [computeStage:{}, chainTaskId:{}, causeCount:{}]",
                        computeStage, chainTaskId, causes.size());
                return List.copyOf(causes);
            } else {
                log.info("Appended exit causes to existing list [computeStage:{}, chainTaskId:{}, newCauseCount:{}, totalCauseCount:{}]",
                        computeStage, chainTaskId, causes.size(), existingCauses.size() + causes.size());
                existingCauses.addAll(causes);
                return List.copyOf(existingCauses);
            }
        });
        return true;
    }

    /**
     * Get exit causes for a specific compute stage and prune them.
     * Returns default unknown issue cause when no specific causes are set.
     *
     * @param computeStage compute stage
     * @param chainTaskId  task ID
     * @return list of exit causes, or default unknown issue if not found
     */
    public List<ReplicateStatusCause> getExitCausesAndPruneForGivenComputeStage(ComputeStage computeStage, String chainTaskId) {
        final String key = buildKey(computeStage, chainTaskId);
        final List<ReplicateStatusCause> causes = exitCauseMap.remove(key);
        if (causes != null) {
            log.debug("Retrieved and pruned exit causes [computeStage:{}, chainTaskId:{}, causeCount:{}]",
                    computeStage, chainTaskId, causes.size());
            return causes;
        } else {
            // Return default unknown issue cause when no specific causes are set
            final ReplicateStatusCause defaultCause = computeStage == ComputeStage.PRE
                    ? ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE
                    : ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
            log.debug("No exit causes found, returning default unknown issue [computeStage:{}, chainTaskId:{}]",
                    computeStage, chainTaskId);
            return List.of(defaultCause);
        }
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
}
