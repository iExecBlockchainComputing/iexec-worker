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

package com.iexec.worker.docker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DockerRegistryConfigurationTests {

    // Get a valid instance of the class under test
    DockerRegistryConfiguration getValidConfiguration() {
        RegistryCredentials credentials = RegistryCredentials.builder()
                .address("address1")
                .username("username1")
                .password("password1")
                .build();
        List<RegistryCredentials> registries = new ArrayList<>();
        registries.add(credentials);
        DockerRegistryConfiguration configuration = new DockerRegistryConfiguration();
        configuration.setRegistries(List.of(credentials));
        return configuration;
    }

    @Test
    public void shouldNotThrowWhenRegistryListIsEmpty() {
        DockerRegistryConfiguration configuration1 = getValidConfiguration();
        configuration1.setRegistries(null);
        assertDoesNotThrow(() -> configuration1.validateRegistries());

        DockerRegistryConfiguration configuration2 = getValidConfiguration();
        configuration2.setRegistries(List.of());
        assertDoesNotThrow(() -> configuration2.validateRegistries());
    }

    @Test
    public void shouldNotThrowWhenRegistryListIsValid() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        assertDoesNotThrow(() -> configuration.validateRegistries());
    }

    @Test
    public void shouldThrowWhenRegistryIsMissingPassword() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        configuration.getRegistries().get(0).setPassword(null);
        assertThrows(Exception.class, () -> configuration.validateRegistries());
    }

    // getRegistryCredentials

    @Test
    public void shouldGetAuthForRegistry() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        RegistryCredentials credentials = configuration.getRegistries().get(0);

        Optional<RegistryCredentials> authForRegistry =
                configuration.getRegistryCredentials(credentials.getAddress());
        assertThat(authForRegistry.isPresent());
        assertThat(authForRegistry.get().getAddress()).isEqualTo(credentials.getAddress());
        assertThat(authForRegistry.get().getUsername()).isEqualTo(credentials.getUsername());
        assertThat(authForRegistry.get().getPassword()).isEqualTo(credentials.getPassword());
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceNoRegistries() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        configuration.setRegistries(null); // no list
        assertThat(configuration.getRegistryCredentials("whatever")).isEmpty();
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceUnknownRegistry() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        assertThat(configuration.getRegistryCredentials("unknownRegistry")).isEmpty();
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceMissingUsernameInConfig() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        RegistryCredentials credentials = configuration.getRegistries().get(0);
        credentials.setUsername(null); // no username
        assertThat(configuration.getRegistryCredentials(credentials.getAddress())).isEmpty();
    }

    @Test
    public void shouldNotGetAuthForRegistrySinceMissingPasswordInConfig() {
        DockerRegistryConfiguration configuration = getValidConfiguration();
        RegistryCredentials credentials = configuration.getRegistries().get(0);
        credentials.setPassword(null); // no password
        assertThat(configuration.getRegistryCredentials(credentials.getAddress())).isEmpty();
    }
}
