/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

/**
 * LAS: local attestation service.
 * Local service used to perform SGX specific operations to attest the enclave
 * (e.g. compute enclave measurement - MREnclave - and attest it through Intel
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
@Value
@ConstructorBinding
@ConfigurationProperties(prefix = "scone")
public class SconeConfiguration {
    boolean showVersion;
    String logLevel;
    SconeRegistry registry;
    int lasPort;

    @Value
    public static class SconeRegistry {
        String name;
        String username;
        String password;
    }
}
