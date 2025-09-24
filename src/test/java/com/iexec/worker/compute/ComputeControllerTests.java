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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ComputeControllerTests {

    public static final String CHAIN_TASK_ID = "0xtask";
    public static final ReplicateStatusCause CAUSE = ReplicateStatusCause.PRE_COMPUTE_INPUT_FILE_DOWNLOAD_FAILED;
    private static final String AUTH_HEADER = "Bearer validToken";
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
    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnOkWhenSendingExitCause(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnOkForSingleCause(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                List.of(CAUSE)
        );

        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnOkForMultipleCauses(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        List<ReplicateStatusCause> multipleCauses = List.of(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
        );

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                multipleCauses
        );

        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnBadRequestForNullCause(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(null)
        );

        assertThat(HttpStatus.BAD_REQUEST.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnBadRequestForNullCausesList(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                null
        );

        assertThat(HttpStatus.BAD_REQUEST.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnBadRequestForEmptyCausesList(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                List.of()
        );

        assertThat(HttpStatus.BAD_REQUEST.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldAccumulateExitCauseWhenCalledMultipleTimes(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        computeStageExitService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, List.of(CAUSE));

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        assertThat(HttpStatus.OK.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnUnauthorizedWhenAuthFails(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        assertThat(HttpStatus.UNAUTHORIZED.value()).isEqualTo(response.getStatusCode().value());
    }

    @ParameterizedTest
    @EnumSource(ComputeStage.class)
    void shouldReturnNotFoundWhenWrongChainTaskId(ComputeStage stage) {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        assertThat(HttpStatus.NOT_FOUND.value()).isEqualTo(response.getStatusCode().value());
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
