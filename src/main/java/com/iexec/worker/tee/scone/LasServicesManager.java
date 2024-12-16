/*
 * Copyright 2022-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee.scone;

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class LasServicesManager implements Purgeable {
    private final SconeConfiguration sconeConfiguration;
    private final TeeServicesPropertiesService teeServicesPropertiesService;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;
    private final String workerWalletAddress;

    /**
     * Memoize Task-LAS container association.
     * As a LAS can be used by multiple tasks, no LAS can be stopped when a task is completed.
     */
    private final Map<String, LasService> chainTaskIdToLasService = ExpiringTaskMapFactory.getExpiringTaskMap();
    /**
     * Memoize LAS image-LAS container association.
     * This avoids starting multiple instances of the same LAS when used by different tasks.
     */
    private final Map<String, LasService> lasImageUriToLasService = new HashMap<>();

    public LasServicesManager(
            SconeConfiguration sconeConfiguration,
            TeeServicesPropertiesService teeServicesPropertiesService,
            WorkerConfigurationService workerConfigService,
            SgxService sgxService,
            DockerService dockerService,
            String workerWalletAddress) {
        this.sconeConfiguration = sconeConfiguration;
        this.teeServicesPropertiesService = teeServicesPropertiesService;
        this.workerConfigService = workerConfigService;
        this.sgxService = sgxService;
        this.dockerService = dockerService;
        this.workerWalletAddress = workerWalletAddress;
    }

    public boolean startLasService(final String chainTaskId) {
        // Just checking no LAS is already created/started for this task
        final LasService alreadyCreatedLas = getLas(chainTaskId);
        if (alreadyCreatedLas != null) {
            return alreadyCreatedLas.isStarted() || alreadyCreatedLas.start();
        }

        final SconeServicesProperties properties = (SconeServicesProperties) teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);
        if (properties == null) {
            log.error("Missing Scone services configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
            return false;
        }
        if (StringUtils.isEmpty(properties.getLasImage())) {
            log.error("Missing Scone LAS OCI image name in Scone services configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
            return false;
        }
        final String lasImageUri = properties.getLasImage();

        final LasService lasService = lasImageUriToLasService.computeIfAbsent(
                lasImageUri,
                this::createLasService);
        chainTaskIdToLasService.putIfAbsent(chainTaskId, lasService);
        return lasService.isStarted() || lasService.start();
    }

    /**
     * Create LAS container name with a random salt to avoid naming conflicts
     * when running multiple workers on the same machine or using multiple SMS.
     * Note: Starting from 64 characters, `failed to lookup address
     * information: Name does not resolve` error occures.
     */
    String createLasContainerName() {
        return "iexec-las-" + workerWalletAddress + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    LasService createLasService(final String lasImageUri) {
        return new LasService(
                createLasContainerName(),
                lasImageUri,
                sconeConfiguration,
                workerConfigService,
                sgxService,
                dockerService);
    }

    @PreDestroy
    void stopLasServices() {
        lasImageUriToLasService.values()
                .forEach(LasService::stopAndRemoveContainer);
    }

    public LasService getLas(final String chainTaskId) {
        return chainTaskIdToLasService.get(chainTaskId);
    }

    /**
     * Try and remove LAS service related to given task ID.
     *
     * @param chainTaskId Task ID whose related LAS service should be purged
     * @return {@literal true} if key is not stored anymore,
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(final String chainTaskId) {
        chainTaskIdToLasService.remove(chainTaskId);
        return !chainTaskIdToLasService.containsKey(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        chainTaskIdToLasService.clear();
    }
}
