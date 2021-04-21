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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * LAS: local attestation service.
 * Local service used to perform SGX specific operations to attest the enclave
 * (eg compute enclave measurement - MREnclave - and attest it through Intel
 * Attestation Service).
 * It must be on the same machine as the attested program/enclave.
 * 
 * MREnclave: an enclave identifier, created by hashing all its
 * code. It guarantees that a code behaves exactly as expected.
 */
@Service
public class SconeLasConfiguration {

    //Eventually rename current service to SconeConfiguration
    @Getter
    @Value("${scone.registry.username}")
    private String registryUsername;

    @JsonIgnore
    @Getter
    @Value("${scone.registry.password}")
    private String registryPassword;

    @Value("${scone.las.image}")
    private String image;

    @Value("${scone.las.version}")
    private String version;

    @Getter
    @Value("${scone.las.port}")
    private int port;

    @Getter
    @Value("${scone.las.dockerNetworkName}")
    private String dockerNetworkName;

    @Getter
    private final String containerName;

    public SconeLasConfiguration(WorkerConfigurationService workerConfigService) {
        // "iexec-las-0xWalletAddress" as containerName to avoid naming conflict
        // when running multiple workers on the same machine.
        containerName = "iexec-las-" + workerConfigService.getWorkerWalletAddress();
    }

    public String getImageUri() {
        return image + ":" + version;
    }

    public String getUrl() {
        return containerName + ":" + port;
    }
}