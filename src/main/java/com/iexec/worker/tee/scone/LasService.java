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

import com.github.dockerjava.api.model.HostConfig;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LasService {
    @Getter
    private final String containerName;
    private final String imageUri;
    @Getter
    private final SconeConfiguration sconeConfig;

    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    @Getter
    private boolean isStarted;

    public LasService(String containerName,
                      String imageUri,
                      SconeConfiguration sconeConfig,
                      WorkerConfigurationService workerConfigService,
                      SgxService sgxService,
                      DockerService dockerService) {
        this.containerName = containerName;
        this.imageUri = imageUri;
        this.sconeConfig = sconeConfig;
        this.workerConfigService = workerConfigService;
        this.sgxService = sgxService;
        this.dockerService = dockerService;
    }

    synchronized boolean start() {
        if (isStarted) {
            return true;
        }

        final HostConfig hostConfig = HostConfig.newHostConfig()
                .withDevices(sgxService.getSgxDevices())
                .withNetworkMode(workerConfigService.getDockerNetworkName());
        final DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .containerName(containerName)
                .imageUri(imageUri)
                .maxExecutionTime(0)
                .build();
        if (!imageUri.contains(sconeConfig.getRegistry().getName())) {
            log.error("LAS image is not from a known registry [image:{}, registry:{}]",
                    imageUri, sconeConfig.getRegistry().getName());
            return false;
        }
        final DockerClientInstance client;
        try {
            client = dockerService.getClient(
                    sconeConfig.getRegistry().getName(),
                    sconeConfig.getRegistry().getUsername(),
                    sconeConfig.getRegistry().getPassword());
        } catch (Exception e) {
            log.error("Failed to get Docker authenticated client to run LAS", e);
            return false;
        }
        if (client == null) {
            log.error("Docker client with credentials is required to enable TEE support");
            return false;
        }
        if (!client.pullImage(imageUri)) {
            log.error("Failed to download LAS image");
            return false;
        }

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Failed to start LAS service");
            return false;
        }

        isStarted = true;
        return true;
    }

    /**
     * Tries to stop and remove this LAS instance container.
     * It is considered successful when the container is not present anymore
     * after the execution of this method.
     *
     * @return {@literal true} if the container is not present anymore,
     * {@literal false} otherwise.
     */
    synchronized boolean stopAndRemoveContainer() {
        if (isStarted()) {
            final DockerClientInstance client = dockerService.getClient();
            isStarted = client.stopAndRemoveContainer(containerName);
        }

        return !isStarted;
    }

    public String getUrl() {
        return containerName + ":" + sconeConfig.getLasPort();
    }
}
