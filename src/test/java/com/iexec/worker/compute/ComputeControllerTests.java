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
import com.iexec.common.worker.api.ExitMessage;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.result.ResultService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ComputeControllerTests {

    public static final String CHAIN_TASK_ID = "0xtask";
    public static final ReplicateStatusCause CAUSE = ReplicateStatusCause.PRE_COMPUTE_INPUT_FILE_DOWNLOAD_FAILED;
    private static final String AUTH_HEADER = "Bearer validToken";
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
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(AUTH_HEADER,
                        ComputeStage.PRE,
                        CHAIN_TASK_ID,
                        new ExitMessage());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
    }

    @Test
    void shouldNotSendExitCauseForComputeComputeStageSinceAlreadySet() {
        when(workerpoolAuthorizationService.isSignedWithEnclaveChallenge(CHAIN_TASK_ID, AUTH_HEADER))
                .thenReturn(true);

        final ComputeStage stage = ComputeStage.PRE;
        computeStageExitService.setExitCause(stage, CHAIN_TASK_ID, CAUSE);

        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                stage,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );

        Assertions.assertEquals(HttpStatus.ALREADY_REPORTED.value(), response.getStatusCode().value());
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
                .thenThrow(new NoSuchElementException());
        final ResponseEntity<Void> response = computeController.sendExitCauseForGivenComputeStage(
                AUTH_HEADER,
                ComputeStage.PRE,
                CHAIN_TASK_ID,
                new ExitMessage(CAUSE)
        );
        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
    }
}
