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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.iexec.common.replicate.ReplicateStatusCause;

class ComputeExitCauseServiceTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";

    private ComputeExitCauseService computeExitCauseService;

    @BeforeEach
    void before() {
        computeExitCauseService = new ComputeExitCauseService();
    }

    //region getPreComputeExitCauseAndPrune
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
    void shouldReturnUnknownIssueWhenPreComputeExitCauseNotSet() {
        ReplicateStatusCause cause = computeExitCauseService.getPreComputeExitCauseAndPrune(CHAIN_TASK_ID);
        Assertions.assertThat(cause)
                .isNotNull()
                .isEqualTo(ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }
    //endregion

    //region getPostComputeExitCauseAndPrune
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
    void shouldReturnUnknownIssueWhenPostComputeExitCauseNotSet() {
        ReplicateStatusCause cause = computeExitCauseService.getPostComputeExitCauseAndPrune(CHAIN_TASK_ID);
        Assertions.assertThat(cause)
                .isNotNull()
                .isEqualTo(ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }
    //endregion

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

    //region bulk exit causes tests
    @Test
    void shouldSetAndGetBulkPreComputeExitCauses() {
        List<ReplicateStatusCause> causes = Arrays.asList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
        );

        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, causes))
                .isTrue();

        List<ReplicateStatusCause> retrieved = computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID);
        Assertions.assertThat(retrieved)
                .isNotNull()
                .hasSize(2)
                .containsExactlyElementsOf(causes);
    }

    @Test
    void shouldSetAndGetBulkPostComputeExitCauses() {
        List<ReplicateStatusCause> causes = Arrays.asList(
                ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND,
                ReplicateStatusCause.POST_COMPUTE_TIMEOUT
        );

        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID, causes))
                .isTrue();

        List<ReplicateStatusCause> retrieved = computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID);
        Assertions.assertThat(retrieved)
                .isNotNull()
                .hasSize(2)
                .containsExactlyElementsOf(causes);
    }

    @Test
    void shouldAccumulateBulkExitCausesWhenCalledMultipleTimes() {
        List<ReplicateStatusCause> firstBatch = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
        List<ReplicateStatusCause> secondBatch = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);

        // First call should succeed
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, firstBatch))
                .isTrue();

        // Second call should also succeed and accumulate causes
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, secondBatch))
                .isTrue();

        // Retrieved causes should contain both batches
        List<ReplicateStatusCause> retrieved = computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID);
        Assertions.assertThat(retrieved)
                .isNotNull()
                .hasSize(2)
                .contains(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING)
                .contains(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);
    }

    @Test
    void shouldAllowDuplicateExitCausesFromDifferentDatasets() {
        List<ReplicateStatusCause> firstBatch = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
        List<ReplicateStatusCause> secondBatch = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING); // Same error for different dataset

        // Both calls should succeed
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, firstBatch))
                .isTrue();
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, secondBatch))
                .isTrue();

        // Retrieved causes should contain both instances of the same error
        List<ReplicateStatusCause> retrieved = computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID);
        Assertions.assertThat(retrieved)
                .isNotNull()
                .hasSize(2)
                .containsOnly(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
    }

    @Test
    void shouldNotSetBulkExitCausesWhenNull() {
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, null))
                .isFalse();
    }

    @Test
    void shouldNotSetBulkExitCausesWhenEmpty() {
        Assertions.assertThat(computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, Collections.emptyList()))
                .isFalse();
    }

    @Test
    void shouldReturnNullWhenBulkExitCausesNotSet() {
        Assertions.assertThat(computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .isNull();
        Assertions.assertThat(computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID))
                .isNull();
    }

    @Test
    void shouldPruneBulkExitCausesAfterRetrieval() {
        List<ReplicateStatusCause> causes = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);

        computeExitCauseService.setBulkExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, causes);

        // First retrieval should return causes
        Assertions.assertThat(computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .isNotNull()
                .containsExactlyElementsOf(causes);

        // Second retrieval should return null (pruned)
        Assertions.assertThat(computeExitCauseService.getBulkExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .isNull();
    }
    //endregion

}
