/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.feign.client;

import java.util.List;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.security.Signature;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import feign.FeignException;


@FeignClient(name = "CoreClient", url = "#{coreConfigurationService.url}")
public interface CoreClient {

    @GetMapping("/version")
    ResponseEntity<String> getCoreVersion() throws FeignException;

    // worker

    @GetMapping("/workers/challenge")
    ResponseEntity<String> getChallenge(@RequestParam(name = "walletAddress") String walletAddress)
            throws FeignException;

    @PostMapping("/workers/login")
    ResponseEntity<String> login(@RequestParam(name = "walletAddress") String walletAddress,
                                 @RequestBody Signature authorization) throws FeignException;

    @PostMapping("/workers/ping")
    ResponseEntity<String> ping(@RequestHeader("Authorization") String bearerToken) throws FeignException;

    @PostMapping("/workers/register")
    ResponseEntity<Void> registerWorker(@RequestHeader("Authorization") String bearerToken,
                                        @RequestBody WorkerModel model) throws FeignException;

    @GetMapping("/workers/config")
    ResponseEntity<PublicConfiguration> getPublicConfiguration() throws FeignException;

    @GetMapping("/workers/computing")
    ResponseEntity<List<String>> getComputingTasks(
            @RequestHeader("Authorization") String bearerToken) throws FeignException;

    // Replicate

    @GetMapping("/replicates/available")
    ResponseEntity<WorkerpoolAuthorization> getAvailableReplicate(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(name = "blockNumber") long blockNumber) throws FeignException;

    @GetMapping("/replicates/interrupted")
    ResponseEntity<List<TaskNotification>> getMissedTaskNotifications(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam(name = "blockNumber") long blockNumber) throws FeignException;

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    ResponseEntity<TaskNotificationType> updateReplicateStatus(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable(name = "chainTaskId") String chainTaskId,
            @RequestBody ReplicateStatusUpdate replicateStatusUpdate) throws FeignException;
}