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

    public boolean setExitCause(String computeStage,
                                String chainTaskId,
                                ReplicateStatusCause exitCause) {
        if (!ComputeStage.isValid(computeStage)) {
            return false;
        }
        String key = buildKey(computeStage, chainTaskId);
        if (!exitCauseMap.containsKey(key)) {
            return false;
        }
        exitCauseMap.put(key, exitCause);
        log.info("Added exit cause [computeStage:{}, chainTaskId:{}, exitCause:{}]",
                computeStage, chainTaskId, exitCause);
        return true;
    }

    private ReplicateStatusCause getReplicateStatusCause(String computeStage,
                                                         String chainTaskId) {
        return exitCauseMap.get(buildKey(computeStage, chainTaskId));
    }

    public ReplicateStatusCause getPreComputeExitCause(String chainTaskId) {
        return getReplicateStatusCause(ComputeStage.PRE, chainTaskId);
    }

    public ReplicateStatusCause getPostComputeExitCause(String chainTaskId) {
        return getReplicateStatusCause(ComputeStage.POST, chainTaskId);
    }

    private String buildKey(String prefix, String chainTaskId) {
        return prefix + chainTaskId;
    }
}