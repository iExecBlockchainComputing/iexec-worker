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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * LAS: local attestation service.
 * Local service used to perform SGX specific operations to attest the enclave
 * (eg compute enclave measurement - MREnclave - and attest it through Intel
 * Attestation Service).
 * It must be on the same machine as the attested program/enclave.
 * <p>
 * MREnclave: an enclave identifier, created by hashing all its
 * code. It guarantees that a code behaves exactly as expected.
 *
 * <p>
 * The following assumes Scontain provides a single registry,
 * within which every LAS image is stored.
 * It also assumes every LAS uses the same port.
 */
@Slf4j
@Configuration
public class SconeConfiguration {

    @Getter
    @Value("${scone.show-version}")
    private boolean showVersion;

    @Getter
    @Value("${scone.log-level}")
    private String logLevel;

    @Getter
    @Value("${scone.registry.name}")
    private String registryName;

    @Getter
    @Value("${scone.registry.username}")
    private String registryUsername;

    @JsonIgnore
    @Getter
    @Value("${scone.registry.password}")
    private String registryPassword;

    @Getter
    @Value("${scone.las-port}")
    private int lasPort;
}