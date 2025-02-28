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


import com.iexec.common.result.ComputedFile;
import com.iexec.common.worker.api.ExitMessage;
import com.iexec.worker.chain.WorkerpoolAuthorizationService;
import com.iexec.worker.result.ResultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

import static org.springframework.http.ResponseEntity.ok;

@RestController
public class ComputeController {

    private final ComputeExitCauseService computeStageExitService;
    private final ResultService resultService;
    private final WorkerpoolAuthorizationService workerpoolAuthorizationService;

    public ComputeController(ComputeExitCauseService computeStageExitService,
                             ResultService resultService,
                             WorkerpoolAuthorizationService workerpoolAuthorizationService) {
        this.computeStageExitService = computeStageExitService;
        this.resultService = resultService;
        this.workerpoolAuthorizationService = workerpoolAuthorizationService;
    }

    @PostMapping("/compute/{stage}/{chainTaskId}/exit")
    public ResponseEntity<Void> sendExitCauseForGivenComputeStage(
            @RequestHeader("Authorization") String authorization,
            @PathVariable ComputeStage stage,
            @PathVariable String chainTaskId,
            @RequestBody ExitMessage exitMessage) {
        try {
            if (!workerpoolAuthorizationService.isSignedWithEnclaveChallenge(chainTaskId, authorization)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .build();
            }
        } catch (NoSuchElementException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND.value())
                    .build();
        }

        if (exitMessage.getCause() == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
        if (!computeStageExitService.setExitCause(stage,
                chainTaskId,
                exitMessage.getCause())) {
            return ResponseEntity
                    .status(HttpStatus.ALREADY_REPORTED.value())
                    .build();
        }
        return ok().build();
    }

    @PostMapping(path = {
            "/iexec_out/{chainTaskId}/computed", //@Deprecated
            "/compute/" + ComputeStage.POST_VALUE + "/{chainTaskId}/computed"
    })
    public ResponseEntity<String> sendComputedFileForTee(@RequestHeader("Authorization") String authorization,
                                                         @PathVariable String chainTaskId,
                                                         @RequestBody ComputedFile computedFile) {
        try {
            if (!workerpoolAuthorizationService.isSignedWithEnclaveChallenge(chainTaskId, authorization)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .build();
            }
        } catch (NoSuchElementException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND.value())
                    .build();
        }
        if (!chainTaskId.equals(computedFile.getTaskId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).build();
        }
        if (!resultService.writeComputedFile(computedFile)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return ok(chainTaskId);
    }

}
