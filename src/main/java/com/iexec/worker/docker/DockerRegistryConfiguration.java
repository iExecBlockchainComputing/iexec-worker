/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.docker;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "docker")
@Getter
@Setter
public class DockerRegistryConfiguration {

    private List<RegistryCredentials> registries;

    /**
     * Min pull timeout expressed in minutes
     */
    @Value("${docker.image.pull-timeout.min:5}")
    private long minPullTimeout;
    /**
     * Max pull timeout expressed in minutes
     */
    @Value("${docker.image.pull-timeout.max:30}")
    private long maxPullTimeout;

    /**
     * Check that if a Docker registry's username is present, then its password is also
     * present, otherwise the worker will fail to start.
     */
    @PostConstruct
    void validateRegistries() throws Exception {
        if (registries == null || registries.isEmpty()) {
            log.warn("Docker registry list is empty");
            return;
        }
        List<RegistryCredentials> registriesWithMissingPasswords = registries.stream()
                // get registries with usernames
                .filter(registryAuth -> StringUtils.isNotBlank(registryAuth.getAddress())
                        && StringUtils.isNotBlank(registryAuth.getUsername()))
                // from those registries get the ones where the password is missing
                .filter(registryAuth -> StringUtils.isBlank(registryAuth.getPassword()))
                .collect(Collectors.toList());
        if (!registriesWithMissingPasswords.isEmpty()) {
            throw new Exception("Missing passwords for registries with usernames: "
                    + registriesWithMissingPasswords);
        }
    }

    /**
     * Get Docker username and password for a given registry address.
     *
     * @param registryAddress address of the registry (docker.io,
     *                        mcr.microsoft.com, ecr.us-east-2.amazonaws.com)
     * @return auth for the registry
     */
    public Optional<RegistryCredentials> getRegistryCredentials(String registryAddress) {
        if (StringUtils.isEmpty(registryAddress) || getRegistries() == null) {
            return Optional.empty();
        }
        return getRegistries().stream()
                .filter(registryAuth -> registryAddress.equals(registryAuth.getAddress())
                        && StringUtils.isNotBlank(registryAuth.getUsername())
                        && StringUtils.isNotBlank(registryAuth.getPassword())
                )
                .findFirst();
    }
}
