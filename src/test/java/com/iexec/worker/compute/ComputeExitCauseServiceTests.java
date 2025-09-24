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
    void shouldSetExitCausesSuccessfully(ComputeStage stage, List<ReplicateStatusCause> causes) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldGetExitCausesAfterSetting(ComputeStage stage, List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(stage, CHAIN_TASK_ID)).containsExactlyElementsOf(causes);
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldReturnDefaultCauseAfterPruning(ComputeStage stage, List<ReplicateStatusCause> causes, ReplicateStatusCause defaultCause) {
        computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes);
        computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(stage, CHAIN_TASK_ID);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(stage, CHAIN_TASK_ID)).isEqualTo(List.of(defaultCause));
    }
    //endregion

    //region Report Once Behavior
    static Stream<Arguments> validExitCauseProvider() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, SINGLE_PRE_CAUSES),
                Arguments.of(ComputeStage.PRE, MULTIPLE_PRE_CAUSES),
                Arguments.of(ComputeStage.POST, SINGLE_POST_CAUSES),
                Arguments.of(ComputeStage.POST, MULTIPLE_POST_CAUSES)
        );
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnTrueWhenReportingForFirstTime(ComputeStage stage, List<ReplicateStatusCause> causes) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithSameCauses(ComputeStage stage, List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithDifferentCauses(ComputeStage stage, List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes);
        List<ReplicateStatusCause> differentCauses = stage == ComputeStage.PRE
                ? List.of(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM)
                : List.of(ReplicateStatusCause.POST_COMPUTE_TIMEOUT);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, differentCauses)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnOriginalCausesAfterSuccessfulReport(ComputeStage stage, List<ReplicateStatusCause> causes) {
        computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(stage, CHAIN_TASK_ID)).isEqualTo(causes);
    }

    @Test
    void shouldAllowReportingPostStageAfterPreStageForSameTask() {
        computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, SINGLE_PRE_CAUSES);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(ComputeStage.POST, CHAIN_TASK_ID, SINGLE_POST_CAUSES)).isTrue();
    }

    static Stream<Arguments> invalidInputArguments() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, null),
                Arguments.of(ComputeStage.PRE, List.of()),
                Arguments.of(ComputeStage.POST, null),
                Arguments.of(ComputeStage.POST, List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInputArguments")
    void shouldReturnFalseForNullOrEmptyCauses(ComputeStage stage, List<ReplicateStatusCause> causes) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, causes))
                .isFalse();
    }

    @Test
    void shouldReturnDefaultCauseWhenNoCausesWereSet() {
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID))
                .isEqualTo(List.of(DEFAULT_PRE_CAUSE));
    }
    //endregion
}
