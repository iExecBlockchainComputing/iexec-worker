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

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.worker.api.ExitMessage;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.result.ResultService;

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
    @Test
    void sendExitCauseForComputeComputeStage() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(AUTH_HEADER,
                        ComputeStage.PRE,
                        CHAIN_TASK_ID,
                        new ExitMessage(CAUSE));
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
    }

    @Test
    void shouldNotSendExitCauseForComputeComputeStageSinceNoCause() {
        final ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(AUTH_HEADER,
                        ComputeStage.PRE,
                        CHAIN_TASK_ID,
                        new ExitMessage(null));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void shouldAccumulateExitCauseWhenCalledMultipleTimes() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ComputeStage stage = ComputeStage.PRE;
        computeStageExitService.setExitCausesForGivenComputeStage(stage, CHAIN_TASK_ID, List.of(CAUSE));

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthFails() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnNotFoundWhenWrongChainTaskId() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);
        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );
        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
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

    // region bulk exit causes tests
    @Test
    void shouldSendBulkPreComputeExitCauses() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        List<ReplicateStatusCause> causes = Arrays.asList(
                ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING,
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM
        );

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                causes
        );

        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
    }

    @Test
    void shouldSendBulkPostComputeExitCauses() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        List<ReplicateStatusCause> causes = Collections.singletonList(
                ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND
        );

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.POST,
                CHAIN_TASK_ID,
                causes
        );

        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnBadRequestForEmptyExitCausesList() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        List<ReplicateStatusCause> causes = Collections.emptyList();

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                causes
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnBadRequestForNullExitCausesList() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                null
        );

        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnUnauthorizedForBulkExitCausesWhenAuthFails() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(false);

        List<ReplicateStatusCause> causes = Collections.singletonList(CAUSE);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                causes
        );

        Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    @Test
    void shouldReturnNotFoundForBulkExitCausesWhenWrongChainTaskId() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenThrow(NoSuchElementException.class);

        List<ReplicateStatusCause> causes = Collections.singletonList(CAUSE);

        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                causes
        );

        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
    }

    @Test
    void shouldAccumulateBulkExitCausesOnMultipleCalls() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        List<ReplicateStatusCause> firstCauses = Collections.singletonList(CAUSE);
        List<ReplicateStatusCause> secondCauses = Collections.singletonList(
                ReplicateStatusCause.PRE_COMPUTE_INVALID_DATASET_CHECKSUM);

        // First call should succeed
        computeStageExitService.setExitCausesForGivenComputeStage(ComputeStage.PRE, CHAIN_TASK_ID, firstCauses);

        // Second call should also succeed and accumulate causes
        final ResponseEntity<Void> response = computeController.sendExitCausesForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                secondCauses
        );

        Assertions.assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
    }
    // endregion
}
