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
import com.iexec.common.worker.api.ExitMessage;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.result.ResultService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ComputeControllerTests {

    public static final String CHAIN_TASK_ID = "0xtask";
    public static final ReplicateStatusCause CAUSE = ReplicateStatusCause.PRE_COMPUTE_INPUT_FILE_DOWNLOAD_FAILED;
    private static final String AUTH_HEADER = "Bearer validToken";
    private static final List<ReplicateStatusCause> MULTIPLE_CAUSES = List.of(
            ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
            ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
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
    private ResponseEntity<Void> getResponse(final String methodName, final ComputeStage stage, final ReplicateStatusCause cause, final List<ReplicateStatusCause> causes) {
        if ("sendExitCauseForGivenComputeStage".equals(methodName)) {
            return computeController.sendExitCauseForGivenComputeStage(
                    AUTH_HEADER,
                    stage,
                    CHAIN_TASK_ID,
                    new ExitMessage(cause)
            );
        } else {
            return computeController.sendExitCausesForGivenComputeStage(
                    AUTH_HEADER,
                    stage,
                    CHAIN_TASK_ID,
                    causes
            );
        }
    }

    static Stream<Arguments> simpleAndListExitCauses() {
        return Stream.of(
                // Null cause via deprecated endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCauseForGivenComputeStage", CAUSE, null),
                Arguments.of(ComputeStage.POST, "sendExitCauseForGivenComputeStage", CAUSE, null),
                // Null causes list via bulk endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCausesForGivenComputeStage", null, List.of(CAUSE)),
                Arguments.of(ComputeStage.POST, "sendExitCausesForGivenComputeStage", null, List.of(CAUSE)),
                // Empty causes list via bulk endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCausesForGivenComputeStage", null, MULTIPLE_CAUSES),
                Arguments.of(ComputeStage.POST, "sendExitCausesForGivenComputeStage", null, MULTIPLE_CAUSES)
        );
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnOkWhenSendingExitCause(final ComputeStage stage, final String methodName, final ReplicateStatusCause cause, final List<ReplicateStatusCause> causes) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        ResponseEntity<Void> response = getResponse(methodName, stage, cause, causes);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldAccumulateExitCauseWhenCalledMultipleTimes(final ComputeStage stage, final String methodName, final ReplicateStatusCause cause, final List<ReplicateStatusCause> causes) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        ReplicateStatusCause initialCause = ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING;
        computeStageExitService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, List.of(initialCause));

        ResponseEntity<Void> response = getResponse(methodName, stage, cause, causes);
        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());

        List<ReplicateStatusCause> allAccumulatedCauses = computeStageExitService
                .getExitCausesAndPruneForGivenComputeStage(stage, CHAIN_TASK_ID);
        List<ReplicateStatusCause> expectedNewCauses = "sendExitCauseForGivenComputeStage".equals(methodName)
                ? List.of(cause)
                : causes;

        assertThat(allAccumulatedCauses)
                .hasSize(1 + expectedNewCauses.size())
                .contains(initialCause)
                .containsAll(expectedNewCauses);
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnUnauthorizedWhenAuthFails(ComputeStage stage, String methodName, ReplicateStatusCause cause, List<ReplicateStatusCause> causes) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        ResponseEntity<Void> response = getResponse(methodName, stage, cause, causes);
        assertThat(HttpStatus.UNAUTHORIZED.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @MethodSource("simpleAndListExitCauses")
    void shouldReturnNotFoundWhenWrongChainTaskId(ComputeStage stage, String methodName, ReplicateStatusCause cause, List<ReplicateStatusCause> causes) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);

        ResponseEntity<Void> response = getResponse(methodName, stage, cause, causes);
        assertThat(HttpStatus.NOT_FOUND.value()).isEqualTo(response.getStatusCode().value());
    }

    static Stream<Arguments> badRequestScenariosArguments() {
        return Stream.of(
                // Null cause via deprecated endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCauseForGivenComputeStage", null, null),
                Arguments.of(ComputeStage.POST, "sendExitCauseForGivenComputeStage", null, null),
                // Null causes list via bulk endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCausesForGivenComputeStage", null, null),
                Arguments.of(ComputeStage.POST, "sendExitCausesForGivenComputeStage", null, null),
                // Empty causes list via bulk endpoint
                Arguments.of(ComputeStage.PRE, "sendExitCausesForGivenComputeStage", null, List.of()),
                Arguments.of(ComputeStage.POST, "sendExitCausesForGivenComputeStage", null, List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("badRequestScenariosArguments")
    void shouldReturnBadRequestForInvalidInputs(ComputeStage stage, String methodName, ReplicateStatusCause cause, List<ReplicateStatusCause> causes) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        ResponseEntity<Void> response = getResponse(methodName, stage, cause, causes);
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
