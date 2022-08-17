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

import com.iexec.common.replicate.ReplicateStatusCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;


@Slf4j
@Service
public class ComputeExitCauseService {

    private final HashMap<String, ReplicateStatusCause> exitCauseMap = new HashMap<>();

    /**
     * Report failure exit cause from pre-compute or post-compute enclave.
     *
     * @param computeStage pre-compute or post-compute-stage label
     * @param chainTaskId  task ID
     * @param exitCause    root cause of the failure
     * @return true if exit cause is reported
     */
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
     */
    ReplicateStatusCause getReplicateStatusCause(ComputeStage computeStage,
                                                         String chainTaskId) {
        return exitCauseMap.get(buildKey(computeStage, chainTaskId));
    }

    /**
     * Get pre-compute exit cause.
     *
     * @param chainTaskId task ID
     * @return exit cause
     */
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
     */
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

}