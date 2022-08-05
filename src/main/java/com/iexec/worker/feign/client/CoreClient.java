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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.security.Signature;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "CoreClient", url = "#{coreConfigurationService.url}")
public interface CoreClient {

    @GetMapping("/version")
    ResponseEntity<String> getCoreVersion();

    //region /workers
    @GetMapping("/workers/challenge")
    ResponseEntity<String> getChallenge(@RequestParam String walletAddress);

    @PostMapping("/workers/login")
    ResponseEntity<String> login(@RequestParam String walletAddress,
                                 @RequestBody Signature authorization);

    @PostMapping("/workers/ping")
    ResponseEntity<String> ping(@RequestHeader String authorization);

    @PostMapping("/workers/register")
    ResponseEntity<Void> registerWorker(@RequestHeader String authorization,
                                        @RequestBody WorkerModel model);

    @GetMapping("/workers/config")
    ResponseEntity<PublicConfiguration> getPublicConfiguration();

    @GetMapping("/workers/computing")
    ResponseEntity<List<String>> getComputingTasks(@RequestHeader String authorization);
    //endregion

    //region /replicates
    @GetMapping("/replicates/available")
    ResponseEntity<WorkerpoolAuthorization> getAvailableReplicate(
            @RequestHeader String authorization,
            @RequestParam long blockNumber);

    @GetMapping("/replicates/interrupted")
    ResponseEntity<List<TaskNotification>> getMissedTaskNotifications(
            @RequestHeader String authorization,
            @RequestParam long blockNumber);

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    ResponseEntity<TaskNotificationType> updateReplicateStatus(
            @RequestHeader String authorization,
            @PathVariable String chainTaskId,
            @RequestBody ReplicateStatusUpdate replicateStatusUpdate);
    //endregion

}
