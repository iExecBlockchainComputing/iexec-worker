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

package com.iexec.worker.tee.scone;

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.util.List;


@Slf4j
@Service
public class TeeSconeService {

    private static final String SCONE_CAS_ADDR = "SCONE_CAS_ADDR";
    private static final String SCONE_LAS_ADDR = "SCONE_LAS_ADDR";
    private static final String SCONE_CONFIG_ID = "SCONE_CONFIG_ID";
    private static final String SCONE_HEAP = "SCONE_HEAP";
    private static final String SCONE_LOG = "SCONE_LOG";
    private static final String SCONE_VERSION = "SCONE_VERSION";
    // private static final String SCONE_MPROTECT = "SCONE_MPROTECT";

    private final SconeConfiguration sconeConfig;
    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final SgxService sgxService;
    private final boolean isLasStarted;

    public TeeSconeService(
            SconeConfiguration sconeConfig,
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            SgxService sgxService) {
        this.sconeConfig = sconeConfig;
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.sgxService = sgxService;
        this.isLasStarted = sgxService.isSgxEnabled() && startLasService();
        if (this.isLasStarted) {
            log.info("Worker can run TEE tasks");
        } else {
            LoggingUtils.printHighlightedMessage("Worker will not run TEE tasks");
        }
    }

    public boolean isTeeEnabled() {
        return isLasStarted;
    }

    boolean startLasService() {
        String lasImage = sconeConfig.getLasImageUri();
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(sconeConfig.getLasContainerName())
                .imageUri(lasImage)
                // application & post-compose enclaves will be
                // able to talk to the LAS via this network
                .dockerNetwork(workerConfigService.getDockerNetworkName())
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .maxExecutionTime(0)
                .build();
        if (!lasImage.contains(sconeConfig.getRegistryName())) {
            throw new RuntimeException(String.format("LAS image (%s) is not " +
                    "from a known registry (%s)", lasImage, sconeConfig.getRegistryName()));
        }
        DockerClientInstance client;
        try {
            client = dockerService.getClient(
                    sconeConfig.getRegistryName(),
                    sconeConfig.getRegistryUsername(),
                    sconeConfig.getRegistryPassword());
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException("Failed to get Docker authenticated client to run LAS");
        }
        if (client == null) {
            log.error("Docker client with credentials is required to enable TEE support");
            return false;
        }
        if (!client.pullImage(lasImage)) {
            log.error("Failed to download LAS image");
            return false;
        }

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Failed to start LAS service");
            return false;
        }
        return true;
    }

    public List<String> buildPreComputeDockerEnv(
            @Nonnull String sessionId,
            long heapSize) {
        String sconeConfigId = sessionId + "/pre-compute";
        return getDockerEnv(sconeConfigId, heapSize);
    }

    public List<String> buildComputeDockerEnv(
            @Nonnull String sessionId,
            long heapSize) {
        String sconeConfigId = sessionId + "/app";
        return getDockerEnv(sconeConfigId, heapSize);
    }

    public List<String> getPostComputeDockerEnv(
            @Nonnull String sessionId,
            long heapSize) {
        String sconeConfigId = sessionId + "/post-compute";
        return getDockerEnv(sconeConfigId, heapSize);
    }

    private List<String> getDockerEnv(String sconeConfigId, long sconeHeap) {
        String sconeVersion = sconeConfig.isShowVersion() ? "1" : "0";
        return List.of(
                SCONE_CAS_ADDR + "=" + sconeConfig.getCasUrl(),
                SCONE_LAS_ADDR + "=" + sconeConfig.getLasUrl(),
                SCONE_CONFIG_ID + "=" + sconeConfigId,
                SCONE_HEAP + "=" + sconeHeap,
                SCONE_LOG + "=" + sconeConfig.getLogLevel(),
                SCONE_VERSION + "=" + sconeVersion);
    }

    @PreDestroy
    private void stopLasService() {
        if (isLasStarted) {
            dockerService.getClient().stopAndRemoveContainer(
                    sconeConfig.getLasContainerName());
        }
    }
}