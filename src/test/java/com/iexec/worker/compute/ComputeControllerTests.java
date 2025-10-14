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
import com.iexec.common.result.ComputedFile;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.workflow.WorkflowError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatusCause.POST_COMPUTE_FAILED_UNKNOWN_ISSUE;
import static com.iexec.common.replicate.ReplicateStatusCause.PRE_COMPUTE_FAILED_UNKNOWN_ISSUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ComputeControllerTests {

    public static final String CHAIN_TASK_ID = "0xtask";
    public static final WorkflowError ERROR = new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INPUT_FILE_DOWNLOAD_FAILED);
    public static final WorkflowError UNKNOWN_PRE_ERROR = new WorkflowError(PRE_COMPUTE_FAILED_UNKNOWN_ISSUE);
    public static final WorkflowError UNKNOWN_POST_ERROR = new WorkflowError(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    private static final String AUTH_HEADER = "Bearer validToken";
    private static final List<WorkflowError> MULTIPLE_ERRORS = List.of(
            new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING),
            new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM)
    );
    private final ComputedFile computedFile = new ComputedFile(
            "/path",
            "callback",
            "task_123",
            "digest_abc",
            "signature",
            null
    );
    private ComputeExitCauseService computeStageExitService;
    @Mock
    private ResultService resultService;
    @Mock
    private WorkerpoolAuthorizationService workerpoolAuthorizationService;
    private ComputeController computeController;

    @BeforeEach
    void setUp() {
        computeStageExitService = new ComputeExitCauseService();
        computeController = new ComputeController(
                computeStageExitService,
                resultService,
                workerpoolAuthorizationService
        );
    }

    // region sendExitCauseForGivenComputeStage
    private ResponseEntity<Void> getResponse(final ComputeStage stage, final List<WorkflowError> errors) {
        return computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                errors
        );
    }

    static Stream<Arguments> simpleAndListExitCauses() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, List.of(ERROR), UNKNOWN_PRE_ERROR),
                Arguments.of(ComputeStage.POST, List.of(ERROR), UNKNOWN_POST_ERROR),
                Arguments.of(ComputeStage.PRE, MULTIPLE_ERRORS, UNKNOWN_PRE_ERROR),
                Arguments.of(ComputeStage.POST, MULTIPLE_ERRORS, UNKNOWN_POST_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnOkWhenSendingExitCause(final ComputeStage stage, final List<WorkflowError> errors) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = getResponse(stage, errors);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnAlreadyReportedWhenCalledMultipleTimes(final ComputeStage stage, final List<WorkflowError> errors, WorkflowError fallbackError) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> firstResponse = getResponse(stage, errors);
        assertThat(firstResponse.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());

        final ResponseEntity<Void> secondResponse = getResponse(stage, errors);
        assertThat(secondResponse.getStatusCode().value()).isEqualTo(HttpStatus.ALREADY_REPORTED.value());

        final List<WorkflowError> retrievedCauses = computeStageExitService
                .getExitCausesAndPruneForGivenComputeStage(CHAIN_TASK_ID, stage, fallbackError);
        assertThat(retrievedCauses)
                .hasSize(errors.size())
                .containsAll(errors);
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnUnauthorizedWhenAuthFails(final ComputeStage stage, final List<WorkflowError> errors) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        final ResponseEntity<Void> response = getResponse(stage, errors);
        assertThat(HttpStatus.UNAUTHORIZED.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnNotFoundWhenWrongChainTaskId(final ComputeStage stage, final List<WorkflowError> errors) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);

        final ResponseEntity<Void> response = getResponse(stage, errors);
        assertThat(HttpStatus.NOT_FOUND.value()).isEqualTo(response.getStatusCode().value());
    }

    static Stream<Arguments> badRequestScenariosArguments() {
        return Stream.of(
                Arguments.of(ComputeStage.PRE, null),
                Arguments.of(ComputeStage.POST, null),
                Arguments.of(ComputeStage.PRE, List.of()),
                Arguments.of(ComputeStage.POST, List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("badRequestScenariosArguments")
    void shouldReturnBadRequestForInvalidInputs(final ComputeStage stage, final List<WorkflowError> errors) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = getResponse(stage, errors);
        assertThat(HttpStatus.BAD_REQUEST.value()).isEqualTo(response.getStatusCode().value());
    }
    // endregion

    // region sendComputedFileForTee
    @Test
    void shouldSendComputedFileForTeeSuccessfully() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);


        computedFile.setTaskId(CHAIN_TASK_ID);

        when(resultService.writeComputedFile(computedFile)).thenReturn(true);

        final ResponseEntity<String> response = computeController.sendComputedFileForTee(
                AUTH_HEADER,
                CHAIN_TASK_ID,
                computedFile
        );

        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        Assertions.assertEquals(CHAIN_TASK_ID, response.getBody());
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthFailsForComputedFile() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        computedFile.setTaskId(CHAIN_TASK_ID);

        final ResponseEntity<String> response = computeController.sendComputedFileForTee(
                AUTH_HEADER,
                CHAIN_TASK_ID,
                computedFile
        );

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnNotFoundWhenWrongChainTaskIdForComputedFile() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);

        computedFile.setTaskId(CHAIN_TASK_ID);

        final ResponseEntity<String> response = computeController.sendComputedFileForTee(
                AUTH_HEADER,
                CHAIN_TASK_ID,
                computedFile
        );

        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnBadRequestWhenTaskIdMismatch() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        computedFile.setTaskId("0x_differentTask");

        final ResponseEntity<String> response = computeController.sendComputedFileForTee(
                AUTH_HEADER,
                CHAIN_TASK_ID,
                computedFile
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnUnauthorizedWhenWriteComputedFileFails() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        computedFile.setTaskId(CHAIN_TASK_ID);

        when(resultService.writeComputedFile(computedFile)).thenReturn(false);

        final ResponseEntity<String> response = computeController.sendComputedFileForTee(
                AUTH_HEADER,
                CHAIN_TASK_ID,
                computedFile
        );

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }
    // endregion
}
