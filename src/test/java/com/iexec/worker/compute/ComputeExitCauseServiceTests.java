/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

import java.util.List;

class ComputeExitCauseServiceTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    private ComputeExitCauseService computeExitCauseService;

    @BeforeEach
    void before() {
        computeExitCauseService = new ComputeExitCauseService();
    }

    //region setAndGetPreComputeExitCauseAndPrune
    @Test
    void setAndGetPreComputeExitCauses() {
        // Test single cause
        ReplicateStatusCause singleCause = ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING;
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE,
                CHAIN_TASK_ID, List.of(singleCause))).isTrue();
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .isEqualTo(List.of(singleCause));

        // Test multiple causes in single call
        List<ReplicateStatusCause> multipleCauses = List.of(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
        );
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE,
                CHAIN_TASK_ID, multipleCauses)).isTrue();
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .containsExactlyElementsOf(multipleCauses);

        // After pruning, should return default unknown issue
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE,
                CHAIN_TASK_ID)).isEqualTo(List.of(ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE));
    }
    //endregion

    //region setAndGetPostComputeExitCauseAndPrune
    @Test
    void setAndGetPostComputeExitCauses() {
        // Test single cause
        ReplicateStatusCause singleCause = ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND;
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.POST,
                CHAIN_TASK_ID, List.of(singleCause))).isTrue();
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID))
                .isEqualTo(List.of(singleCause));

        // Test multiple causes in single call
        List<ReplicateStatusCause> multipleCauses = List.of(
                ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND,
                ReplicateStatusCause.POST_COMPUTE_TIMEOUT
        );
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.POST,
                CHAIN_TASK_ID, multipleCauses)).isTrue();
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID))
                .containsExactlyElementsOf(multipleCauses);

        // After pruning, should return default unknown issue
        Assertions.assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.POST,
                CHAIN_TASK_ID)).isEqualTo(List.of(ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE));
    }
    //endregion

    //region Accumulation
    @Test
    void shouldAccumulateExitCausesWhenCalledMultipleTimes() {
        // Test accumulation with same cause
        ReplicateStatusCause duplicateCause = ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING;
        computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, List.of(duplicateCause));
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE,
                CHAIN_TASK_ID, List.of(duplicateCause))).isTrue();

        List<ReplicateStatusCause> retrieved = computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID);
        Assertions.assertThat(retrieved)
                .hasSize(2)
                .containsExactly(duplicateCause, duplicateCause);

        // Test accumulation with different causes
        List<ReplicateStatusCause> firstBatch = List.of(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
        List<ReplicateStatusCause> secondBatch = List.of(
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);

        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID, firstBatch))
                .isTrue();
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID, secondBatch))
                .isTrue();

        List<ReplicateStatusCause> accumulatedCauses = computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID);
        Assertions.assertThat(accumulatedCauses)
                .hasSize(2)
                .contains(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING)
                .contains(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);
    }

    @Test
    void shouldNotSetExitCausesWhenNullOrEmpty() {
        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, null))
                .isFalse();

        Assertions.assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, List.of()))
                .isFalse();
    }
    //endregion

}
