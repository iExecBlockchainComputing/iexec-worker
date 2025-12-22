/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee.tdx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Device;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TdxServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.sms.TeeSessionGenerationException;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
public class TeeTdxService extends TeeService implements Purgeable {
    private final String secretProviderAgent;
    private final WorkerConfigurationService workerConfigurationService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TdxSession> tdxSessions = new ConcurrentHashMap<>();

    public TeeTdxService(final SmsService smsService,
                         final TeeServicesPropertiesService teeServicesPropertiesService,
                         @Value("${tee.tdx.secret-provider-agent}") final String secretProviderAgent,
                         final WorkerConfigurationService workerConfigurationService) {
        super(smsService, teeServicesPropertiesService);
        this.secretProviderAgent = secretProviderAgent;
        this.workerConfigurationService = workerConfigurationService;
    }

    @Override
    public boolean isTeeEnabled() {
        // FIXME add service to check TDX compatibility
        return true;
    }

    @Override
    public void createTeeSession(final WorkerpoolAuthorization workerpoolAuthorization) throws TeeSessionGenerationException {
        super.createTeeSession(workerpoolAuthorization);
        final String chainTaskId = workerpoolAuthorization.getChainTaskId();
        final TeeSessionGenerationResponse teeSession = getTeeSession(chainTaskId);
        try {
            final String provisioningUrl = teeSession.getSecretProvisioningUrl();
            final String sessionId = teeSession.getSessionId();
            Files.createDirectories(Path.of(workerConfigurationService.getTaskBaseDir(chainTaskId)));
            final String fileName = String.format("session-%s.json", chainTaskId);
            final String filePath = String.format("%s/%s", workerConfigurationService.getTaskBaseDir(chainTaskId), fileName);
            final Process process = new ProcessBuilder(
                    secretProviderAgent, "-e", provisioningUrl, "-i", sessionId, "-s", fileName, "-v", "nullverifier")
                    .directory(Path.of(workerConfigurationService.getTaskBaseDir(chainTaskId)).toFile())
                    .start();
            final int status = process.waitFor();
            log.info("process ended [status:{}, provisioning:{}, file-path:{}]",
                    status, provisioningUrl, filePath);
            try (final BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = output.readLine()) != null) {
                    log.info("line {}", line);
                }
            }
            final File sessionFile = new File(filePath);
            final TdxSession taskSession = mapper.readValue(sessionFile, TdxSession.class);
            tdxSessions.put(chainTaskId, taskSession);
            final TdxServicesProperties properties = new TdxServicesProperties(
                    "", // no meaning as the SMS is started with a single configuration for now
                    TeeAppProperties.builder().image(getService(chainTaskId, "pre-compute").findFirst().map(TdxSession.Service::image_name).orElse("")).build(),
                    TeeAppProperties.builder().image(getService(chainTaskId, "post-compute").findFirst().map(TdxSession.Service::image_name).orElse("")).build());
            teeServicesPropertiesService.putTeeServicesPropertiesForTask(chainTaskId, properties);
        } catch (IOException e) {
            log.warn("process did not execute", e);
            throw new TeeSessionGenerationException(TeeSessionGenerationError.SECURE_SESSION_STORAGE_CALL_FAILED);
        } catch (InterruptedException e) {
            log.error("thread has been interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean prepareTeeForTask(final String chainTaskId) {
        return true;
    }

    @Override
    public List<String> buildPreComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(taskDescription.getChainTaskId(), "pre-compute");
    }

    @Override
    public List<String> buildComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(taskDescription.getChainTaskId(), "app");
    }

    @Override
    public List<String> buildPostComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(taskDescription.getChainTaskId(), "post-compute");
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        return List.of();
    }

    @Override
    public List<Device> getDevices() {
        return List.of();
    }

    private List<String> getDockerEnv(final String chainTaskId, final String serviceName) {
        return getService(chainTaskId, serviceName)
                .findFirst()
                .map(service -> service.environment().entrySet().stream()
                        .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                        .toList()).orElseGet(List::of);
    }

    private Stream<TdxSession.Service> getService(final String chainTaskId, final String serviceName) {
        final TdxSession session = tdxSessions.get(chainTaskId);
        if (session == null) {
            return Stream.empty();
        }
        return session.services().stream()
                .filter(service -> Objects.equals(serviceName, service.name()));
    }

    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        tdxSessions.remove(chainTaskId);
        return super.purgeTask(chainTaskId) && !tdxSessions.containsKey(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        tdxSessions.clear();
        super.purgeAllTasksData();
    }
}
