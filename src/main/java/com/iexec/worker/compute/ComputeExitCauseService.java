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

import com.iexec.worker.workflow.WorkflowError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;


@Slf4j
@Service
public class ComputeExitCauseService {

    private final HashMap<String, List<WorkflowError>> exitCauseMap = new HashMap<>();

    /**
     * Report failure exit causes from pre-compute or post-compute enclave.
     * Guarantees that exit causes can only be reported once per compute stage and task.
     *
     * @param computeStage pre-compute or post-compute-stage label
     * @param chainTaskId  task ID
     * @param errors       list of workflow errors describing the failure
     * @return true if exit causes are reported, false if already reported
     */
    boolean setExitCausesForGivenComputeStage(final String chainTaskId,
                                              final ComputeStage computeStage,
                                              final List<WorkflowError> errors) {
        final String key = buildKey(computeStage, chainTaskId);

        if (exitCauseMap.containsKey(key)) {
            log.warn("Exit causes already reported for compute stage [chainTaskId:{}, computeStage:{}]",
                    chainTaskId, computeStage);
            return false;
        }

        exitCauseMap.put(key, List.copyOf(errors));
        log.info("Added exit causes [chainTaskId:{}, computeStage:{}, causeCount:{}]",
                chainTaskId, computeStage, errors.size());
        return true;
    }

    /**
     * Get exit causes for a specific compute stage and prune them.
     * Returns default unknown issue cause when no specific causes are set.
     *
     * @param computeStage   compute stage
     * @param chainTaskId    task ID
     * @param fallbackError  default error to return if no specific causes are found
     * @return list of workflow errors, or default unknown issue if not found
     */
    public List<WorkflowError> getExitCausesAndPruneForGivenComputeStage(
            final String chainTaskId,
            final ComputeStage computeStage,
            final WorkflowError fallbackError) {
        final String key = buildKey(computeStage, chainTaskId);
        final List<WorkflowError> errors = exitCauseMap.remove(key);
        if (errors != null) {
            log.info("Retrieved and pruned exit causes [chainTaskId:{} computeStage:{}, causeCount:{}]",
                    chainTaskId, computeStage, errors.size());
            return errors;
        } else {
            log.info("No exit causes found, returning fallback cause [chainTaskId:{}, computeStage:{}]",
                    chainTaskId, computeStage);
            return List.of(fallbackError);
        }
    }

    /**
     * Build key for storing or retrieving an exit cause
     *
     * @param prefix      compute stage prefix
     * @param chainTaskId task ID
     * @return exit cause storage key
     */
    private String buildKey(final ComputeStage prefix, final String chainTaskId) {
        return prefix + "_" + chainTaskId;
    }
}
