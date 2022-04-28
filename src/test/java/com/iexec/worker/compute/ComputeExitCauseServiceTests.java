/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComputeExitCauseServiceTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    private ComputeExitCauseService computeExitCauseService;

    @BeforeEach
    void before() {
        computeExitCauseService = new ComputeExitCauseService();
    }

    @Test
    void setAndGetPreComputeExitCauseAndPrune() {
        ReplicateStatusCause cause =
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING;
        Assertions.assertThat(computeExitCauseService.setExitCause(ComputeStage.PRE,
                CHAIN_TASK_ID,
                cause)).isTrue();
        Assertions.assertThat(computeExitCauseService.getPreComputeExitCauseAndPrune(CHAIN_TASK_ID))
                .isEqualTo(cause);
        Assertions.assertThat(computeExitCauseService.getReplicateStatusCause(ComputeStage.PRE,
                CHAIN_TASK_ID)).isNull();
    }

    @Test
    void setAndGetPostComputeExitCauseAndPrune() {
        ReplicateStatusCause cause =
                ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND;
        Assertions.assertThat(computeExitCauseService.setExitCause(ComputeStage.POST,
                CHAIN_TASK_ID,
                cause)).isTrue();
        Assertions.assertThat(computeExitCauseService.getPostComputeExitCauseAndPrune(CHAIN_TASK_ID))
                .isEqualTo(cause);
        Assertions.assertThat(computeExitCauseService.getReplicateStatusCause(ComputeStage.POST,
                CHAIN_TASK_ID)).isNull();
    }

    @Test
    void shouldNotSetComputeExitCauseSinceAlreadySet() {
        ReplicateStatusCause cause =
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING;
        computeExitCauseService.setExitCause(ComputeStage.POST,
                CHAIN_TASK_ID,
                cause);
        Assertions.assertThat(computeExitCauseService.setExitCause(ComputeStage.POST,
                CHAIN_TASK_ID,
                cause)).isFalse();
    }

}