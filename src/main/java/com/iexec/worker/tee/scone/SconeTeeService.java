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
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.List;


@Slf4j
@Service
public class SconeTeeService {

    private final SconeLasConfiguration sconeLasConfig;
    private final boolean isLasStarted;
    private final DockerService dockerService;

    public SconeTeeService(
            SgxService sgxService,
            SconeLasConfiguration sconeLasConfig,
            DockerService dockerService
    ) {
        this.sconeLasConfig = sconeLasConfig;
        this.dockerService = dockerService;
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

        if (!dockerService.getClient().pullImage(sconeLasConfig.getImageUri())) {
            return false;
        }

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Couldn't start LAS service, will continue without TEE support");
            return false;
        }
        return true;
    }

    public List<String> buildSconeDockerEnv(String sconeConfigId, String sconeCasUrl, String sconeHeap) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeCasUrl)
                .sconeConfigId(sconeConfigId)
                .sconeHeap(sconeHeap)
                .build();

        return sconeConfig.toDockerEnv();
    }

    @PreDestroy
    void stopLasService() {
        if (isLasStarted) {
            dockerService.getClient().stopAndRemoveContainer(sconeLasConfig.getContainerName());
        }
    }
}