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
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.PreDestroy;
import java.util.List;


@Slf4j
@Service
public class SconeTeeService {

    private static final String PRE_COMPUTE_HEAP_SIZE = "4G";
    private static final String COMPUTE_HEAP_SIZE = "1G";
    private static final String POST_COMPUTE_HEAP_SIZE = "3G";

    private final SconeLasConfiguration sconeLasConfig;
    private final DockerService dockerService;
    private final PublicConfigurationService publicConfigService;
    private final boolean isLasStarted;

    public SconeTeeService(
            SconeLasConfiguration sconeLasConfig,
            DockerService dockerService,
            PublicConfigurationService publicConfigService,
            SgxService sgxService
    ) {
        this.sconeLasConfig = sconeLasConfig;
        this.dockerService = dockerService;
        this.publicConfigService = publicConfigService;
        this.isLasStarted = sgxService.isSgxEnabled() && startLasService();
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
                .dockerNetwork(sconeLasConfig.getDockerNetworkName())
                .isSgx(true)
                .maxExecutionTime(0)
                .build();
        DockerClientInstance client =
                dockerService.getClient(sconeLasConfig.getRegistryUsername(),
                        sconeLasConfig.getRegistryPassword());
        if (client == null) {
            return false;
        }
        if (!client.pullImage(sconeLasConfig.getImageUri())) {
            return false;
        }

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Couldn't start LAS service, will continue without TEE support");
            return false;
        }
        return true;
    }

    public List<String> getPreComputeDockerEnv(@Nonnull String sessionId) {
        String sconeConfigId = sessionId + "/pre-compute";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(publicConfigService.getSconeCasURL())
                .sconeConfigId(sconeConfigId)
                .sconeHeap(PRE_COMPUTE_HEAP_SIZE)
                .build()
                .toDockerEnv();
    }

    public List<String> getComputeDockerEnv(@Nonnull String sessionId) {
        String sconeConfigId = sessionId + "/app";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(publicConfigService.getSconeCasURL())
                .sconeConfigId(sconeConfigId)
                .sconeHeap(COMPUTE_HEAP_SIZE)
                .build()
                .toDockerEnv();
    }

    public List<String> getPostComputeDockerEnv(@Nonnull String sessionId) {
        String sconeConfigId = sessionId + "/post-compute";
        return SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(publicConfigService.getSconeCasURL())
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