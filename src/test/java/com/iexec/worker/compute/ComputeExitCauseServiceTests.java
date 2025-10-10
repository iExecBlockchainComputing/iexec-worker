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
import com.iexec.worker.workflow.WorkflowError;
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
    private static final WorkflowError DEFAULT_PRE_ERROR = WorkflowError.builder().cause(ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE).build();
    private static final WorkflowError DEFAULT_POST_ERROR = WorkflowError.builder().cause(ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build();
    private static final List<WorkflowError> SINGLE_PRE_ERRORS = List.of(WorkflowError.builder().cause(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING).build());
    private static final List<WorkflowError> MULTIPLE_PRE_ERRORS = List.of(
            WorkflowError.builder().cause(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING).build(),
            WorkflowError.builder().cause(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM).build());
    private static final List<WorkflowError> SINGLE_POST_ERRORS = List.of(WorkflowError.builder().cause(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND).build());
    private static final List<WorkflowError> MULTIPLE_POST_ERRORS = List.of(
            WorkflowError.builder().cause(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND).build(),
            WorkflowError.builder().cause(ReplicateStatusCause.POST_COMPUTE_TIMEOUT).build());

    private ComputeExitCauseService computeExitCauseService;

    @BeforeEach
    void before() {
        computeExitCauseService = new ComputeExitCauseService();
    }

    //region setAndGetExitCausesParameterizedTests
    static Stream<Arguments> computeStageAndCausesArguments() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, SINGLE_PRE_ERRORS, DEFAULT_PRE_ERROR),
                Arguments.of(ComputeStage.PRE, MULTIPLE_PRE_ERRORS, DEFAULT_PRE_ERROR),
                Arguments.of(ComputeStage.POST, SINGLE_POST_ERRORS, DEFAULT_POST_ERROR),
                Arguments.of(ComputeStage.POST, MULTIPLE_POST_ERRORS, DEFAULT_POST_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldSetExitCausesSuccessfully(final ComputeStage stage, final List<WorkflowError> errors) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldGetExitCausesAfterSetting(final ComputeStage stage, final List<WorkflowError> errors, final WorkflowError defaultError) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultError)).containsExactlyElementsOf(errors);
    }

    @ParameterizedTest
    @MethodSource("computeStageAndCausesArguments")
    void shouldReturnDefaultCauseAfterPruning(final ComputeStage stage, final List<WorkflowError> errors, final WorkflowError defaultError) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors);
        computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultError);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultError)).isEqualTo(List.of(defaultError));
    }
    //endregion

    //region Report Once Behavior
    static Stream<Arguments> validExitCauseProvider() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, SINGLE_PRE_ERRORS, DEFAULT_PRE_ERROR),
                Arguments.of(ComputeStage.PRE, MULTIPLE_PRE_ERRORS, DEFAULT_PRE_ERROR),
                Arguments.of(ComputeStage.POST, SINGLE_POST_ERRORS, DEFAULT_POST_ERROR),
                Arguments.of(ComputeStage.POST, MULTIPLE_POST_ERRORS, DEFAULT_POST_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnTrueWhenReportingForFirstTime(final ComputeStage stage, final List<WorkflowError> errors) {
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithSameCauses(final ComputeStage stage, final List<WorkflowError> errors) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnFalseWhenReportingTwiceWithDifferentCauses(final ComputeStage stage, final List<WorkflowError> errors) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors);
        List<WorkflowError> differentCauses = stage == ComputeStage.PRE
                ? List.of(WorkflowError.builder().cause(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM).build())
                : List.of(WorkflowError.builder().cause(ReplicateStatusCause.POST_COMPUTE_TIMEOUT).build());
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, differentCauses)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validExitCauseProvider")
    void shouldReturnOriginalCausesAfterSuccessfulReport(final ComputeStage stage, final List<WorkflowError> errors, final WorkflowError defaultError) {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, stage, errors);
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, defaultError)).isEqualTo(errors);
    }

    @Test
    void shouldAllowReportingPostStageAfterPreStageForSameTask() {
        computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.PRE, SINGLE_PRE_ERRORS);
        assertThat(computeExitCauseService.setExitCausesForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.POST, SINGLE_POST_ERRORS)).isTrue();
    }

    @Test
    void shouldReturnDefaultCauseWhenNoCausesWereSet() {
        assertThat(computeExitCauseService.getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, ComputeStage.PRE, DEFAULT_PRE_ERROR))
                .isEqualTo(List.of(DEFAULT_PRE_ERROR));
    }
    //endregion
}
