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
    boolean setExitCause(String computeStage,
                         String chainTaskId,
                         ReplicateStatusCause exitCause) {
        if (!ComputeStage.isValid(computeStage)) {
            log.info("Cannot set exit cause with invalid stage " +
                            "[computeStage:{}, chainTaskId:{}, exitCause:{}]",
                    computeStage, chainTaskId, exitCause);
            return false;
        }
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
    ReplicateStatusCause getReplicateStatusCause(String computeStage,
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
        String stage = ComputeStage.PRE;
        ReplicateStatusCause cause = getReplicateStatusCause(stage, chainTaskId);
        pruneExitCause(stage, chainTaskId);
        return cause;
    }

    /**
     * Get post-compute exit cause.
     *
     * @param chainTaskId task ID
     * @return exit cause
     */
    public ReplicateStatusCause getPostComputeExitCauseAndPrune(String chainTaskId) {
        String stage = ComputeStage.POST;
        ReplicateStatusCause cause = getReplicateStatusCause(stage, chainTaskId);
        pruneExitCause(stage, chainTaskId);
        return cause;
    }

    /**
     * Build key for storing or retrieving an exit cause
     *
     * @param prefix      compute stage prefix
     * @param chainTaskId task ID
     * @return exit cause storage key
     */
    private String buildKey(String prefix, String chainTaskId) {
        return prefix + "_" + chainTaskId;
    }

    /**
     * Prune exit cause.
     *
     * @param computeStage compute stage
     * @param chainTaskId  task ID
     */
    private void pruneExitCause(String computeStage, String chainTaskId) {
        if (ComputeStage.isValid(computeStage)) {
            exitCauseMap.remove(buildKey(computeStage, chainTaskId));
        }
    }

}