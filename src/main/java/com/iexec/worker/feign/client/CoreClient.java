package com.iexec.worker.feign.client;

import feign.FeignException;

import java.util.List;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.security.Signature;
import com.iexec.worker.feign.config.FeignConfiguration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(name = "CoreClient",
        url = "#{coreConfigurationService.url}",
        configuration = FeignConfiguration.class)
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