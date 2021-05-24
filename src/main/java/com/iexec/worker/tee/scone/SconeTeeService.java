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
public class SconeTeeService {

    private static final String POST_COMPUTE_HEAP_SIZE = "4G";

    private final SconeLasConfiguration sconeLasConfig;
    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final boolean isLasStarted;

    public SconeTeeService(
            SconeLasConfiguration sconeLasConfig,
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            SgxService sgxService) {
        this.sconeLasConfig = sconeLasConfig;
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
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
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(sconeLasConfig.getContainerName())
                .imageUri(sconeLasConfig.getImageUri())
                // application & post-compose enclaves will be
                // able to talk to the LAS via this network
                .dockerNetwork(workerConfigService.getDockerNetworkName())
                .isSgx(true)
                .maxExecutionTime(0)
                .build();
        DockerClientInstance client =
                dockerService.getClient(sconeLasConfig.getRegistryUsername(),
                        sconeLasConfig.getRegistryPassword());
        if (client == null) {
            log.error("Docker client with credentials is required to enable TEE support");
            return false;
        }
        if (!client.pullImage(sconeLasConfig.getImageUri())) {
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

    public List<String> buildPreComputeDockerEnv(@Nonnull String sessionId,
                                                 String heapSize) {
        String sconeConfigId = sessionId + "/pre-compute";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeLasConfig.getSconeCasUrl())
                .sconeConfigId(sconeConfigId)
                .sconeHeap(heapSize)
                .build()
                .toDockerEnv();
    }

    public List<String> buildComputeDockerEnv(@Nonnull String sessionId,
                                              long heapSize) {
        String sconeConfigId = sessionId + "/app";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeLasConfig.getSconeCasUrl())
                .sconeConfigId(sconeConfigId)
                .sconeHeap(String.valueOf(heapSize))
                .build()
                .toDockerEnv();
    }

    public List<String> getPostComputeDockerEnv(@Nonnull String sessionId) {
        String sconeConfigId = sessionId + "/post-compute";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeLasConfig.getSconeCasUrl())
                .sconeConfigId(sconeConfigId)
                .sconeHeap(POST_COMPUTE_HEAP_SIZE)
                .build()
                .toDockerEnv();
    }

    @PreDestroy
    void stopLasService() {
        if (isLasStarted) {
            dockerService.getClient().stopAndRemoveContainer(sconeLasConfig.getContainerName());
        }
    }
}