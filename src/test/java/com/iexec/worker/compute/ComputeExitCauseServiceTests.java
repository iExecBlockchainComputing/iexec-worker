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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeExitCauseServiceTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";
    private static final ReplicateStatusCause DEFAULT_PRE_CAUSE = ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE;
    private static final ReplicateStatusCause DEFAULT_POST_CAUSE = ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
    private static final List<ReplicateStatusCause> SINGLE_PRE_CAUSES = List.of(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
    private static final List<ReplicateStatusCause> MULTIPLE_PRE_CAUSES = List.of(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING, ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);
    private static final List<ReplicateStatusCause> SINGLE_POST_CAUSES = List.of(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND);
    private static final List<ReplicateStatusCause> MULTIPLE_POST_CAUSES = List.of(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND, ReplicateStatusCause.POST_COMPUTE_TIMEOUT);

    private ComputeExitCauseService computeExitCauseService;

    @BeforeEach
    void before() {
        computeExitCauseService = new ComputeExitCauseService();
    }

    //region setAndGetExitCausesParameterizedTests
    static Stream<Arguments> computeStageAndCausesArguments() {
        return Stream.of(
                Arguments.of(
                        ComputeStage.PRE,
                        SINGLE_PRE_CAUSES,
                        DEFAULT_PRE_CAUSE
                ),
                Arguments.of(
                        ComputeStage.PRE,
                        List.of(
                                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
                        ),
                        DEFAULT_PRE_CAUSE
                ),
                Arguments.of(
                        ComputeStage.POST,
                        SINGLE_POST_CAUSES,
                        DEFAULT_POST_CAUSE
                ),
                Arguments.of(
                        ComputeStage.POST,
                        List.of(
                                ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND,
                                ReplicateStatusCause.POST_COMPUTE_TIMEOUT
                        ),
                        DEFAULT_POST_CAUSE
                )
        );
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldSetExitCausesSuccessfully(final ComputeStage stage, final List<ReplicateStatusCause> causes) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldGetExitCausesAfterSetting(final ComputeStage stage, final List<ReplicateStatusCause> causes, final ReplicateStatusCause defaultCause) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultCause)).containsExactlyElementsOf(causes);
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldReturnDefaultCauseAfterPruning(final ComputeStage stage, final List<ReplicateStatusCause> causes, final ReplicateStatusCause defaultCause) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes);
        computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultCause);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultCause)).isEqualTo(List.of(defaultCause));
    }
    //endregion

    //region Report Once Behavior
    static Stream<Arguments> validExitCauseProvider() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, SINGLE_PRE_CAUSES, DEFAULT_PRE_CAUSE),
                Arguments.of(ComputeStage.PRE, MULTIPLE_PRE_CAUSES, DEFAULT_PRE_CAUSE),
                Arguments.of(ComputeStage.POST, SINGLE_POST_CAUSES, DEFAULT_POST_CAUSE),
                Arguments.of(ComputeStage.POST, MULTIPLE_POST_CAUSES, DEFAULT_POST_CAUSE)
        );
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnTrueWhenReportingForFirstTime(final ComputeStage stage, final List<ReplicateStatusCause> causes) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithSameCauses(final ComputeStage stage, final List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithDifferentCauses(final ComputeStage stage, final List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes);
        List<ReplicateStatusCause> differentCauses = stage == ComputeStage.PRE
                ? List.of(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM)
                : List.of(ReplicateStatusCause.POST_COMPUTE_TIMEOUT);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, differentCauses)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnOriginalCausesAfterSuccessfulReport(final ComputeStage stage, final List<ReplicateStatusCause> causes, final ReplicateStatusCause defaultCause) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, causes);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultCause)).isEqualTo(causes);
    }

    @Test
    void shouldAllowReportingPostStageAfterPreStageForSameTask() {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.PRE, SINGLE_PRE_CAUSES);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.POST, SINGLE_POST_CAUSES)).isTrue();
    }

    @Test
    void shouldReturnDefaultCauseWhenNoCausesWereSet() {
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.PRE, DEFAULT_PRE_CAUSE))
                .isEqualTo(List.of(DEFAULT_PRE_CAUSE));
    }
    //endregion
}
