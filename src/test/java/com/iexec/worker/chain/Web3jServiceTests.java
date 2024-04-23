/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.chain;

import com.iexec.worker.config.ConfigServerConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class Web3jServiceTests {
    @Mock
    private ConfigServerConfigurationService configServerConfigurationService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(configServerConfigurationService.getChainId()).thenReturn(134);
        when(configServerConfigurationService.getBlockTime()).thenReturn(Duration.ofSeconds(5));
        when(configServerConfigurationService.isSidechain()).thenReturn(true);
        when(configServerConfigurationService.getChainNodeUrl()).thenReturn("https://bellecour.iex.ec");
        when(workerConfigurationService.getGasPriceMultiplier()).thenReturn(1.0f);
        when(workerConfigurationService.getGasPriceCap()).thenReturn(22_000_000_000L);
    }

    @Test
    void shouldCreateInstanceWithDefaultNodeAddress() {
        when(workerConfigurationService.getOverrideBlockchainNodeAddress()).thenReturn("");
        assertThat(new Web3jService(configServerConfigurationService, workerConfigurationService)).isNotNull();
    }

    @Test
    void shouldCreateInstanceWithOverridenNodeAddress() {
        when(workerConfigurationService.getOverrideBlockchainNodeAddress()).thenReturn("http://localhost:8545");
        assertThat(new Web3jService(configServerConfigurationService, workerConfigurationService)).isNotNull();
    }
}
