/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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
import com.iexec.worker.result.ResultService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.ResponseEntity;

class ComputeControllerTests {

    public static final String CHAIN_TASK_ID = "0xtask";
    public static final ReplicateStatusCause CAUSE = ReplicateStatusCause.PRE_COMPUTE_INPUT_FILE_DOWNLOAD_FAILED;
    private ComputeExitCauseService computeStageExitService;
    @Mock
    private ResultService resultService;
    private ComputeController computeController;

    @BeforeEach
    void setUp() {
        computeStageExitService = new ComputeExitCauseService();
        computeController = new ComputeController(computeStageExitService, resultService);
    }

    @Test
    void sendExitCauseForComputeComputeStage() {
        ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(ComputeStage.PRE,
                        CHAIN_TASK_ID,
                        new ExitMessage(CAUSE));
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void shouldNotSendExitCauseForComputeComputeStageSinceInvalidStage() {
        ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage("invalid",
                        CHAIN_TASK_ID,
                        new ExitMessage());
        Assertions.assertTrue(response.getStatusCode().is4xxClientError());
        Assertions.assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void shouldNotSendExitCauseForComputeComputeStageSinceNoCause() {
        ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(ComputeStage.PRE,
                        CHAIN_TASK_ID,
                        new ExitMessage());
        Assertions.assertTrue(response.getStatusCode().is4xxClientError());
        Assertions.assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void shouldNotSendExitCauseForComputeComputeStageSinceAlreadySet() {
        String stage = ComputeStage.PRE;
        computeStageExitService.setExitCause(stage, CHAIN_TASK_ID, CAUSE);
        //try to re set
        ResponseEntity<?> response =
                computeController.sendExitCauseForGivenComputeStage(stage,
                        CHAIN_TASK_ID,
                        new ExitMessage(CAUSE));
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assertions.assertEquals(208, response.getStatusCode().value());
    }
    
}