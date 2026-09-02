/*
 * Copyright 2023-2026 IEXEC BLOCKCHAIN TECH
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Web3jServiceTests {
    private static final String RPC_DEFAULT_URL = "https://sepolia-rollup.arbitrum.io/rpc";
    private static final String RPC_OVERRIDE_URL = "https://sepolia-rollup-override.arbitrum.io/rpc";

    @Mock
    private ConfigServerConfigurationService configServerConfigurationService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;

    @BeforeEach
    void init() {
        when(configServerConfigurationService.getChainId()).thenReturn(421614);
        when(configServerConfigurationService.getBlockTime()).thenReturn(Duration.ofSeconds(5));
        when(configServerConfigurationService.isSidechain()).thenReturn(false);
        when(workerConfigurationService.getGasPriceMultiplier()).thenReturn(1.1f);
        when(workerConfigurationService.getGasPriceCap()).thenReturn(22_000_000_000L);
    }

    @Test
    void shouldCreateInstanceWithDefaultNodeAddress() {
        when(workerConfigurationService.getOverrideBlockchainNodeAddress()).thenReturn("");
        when(configServerConfigurationService.getChainNodeUrl()).thenReturn(RPC_DEFAULT_URL);
        final Web3jService web3jService = new Web3jService(configServerConfigurationService, workerConfigurationService);
        assertThat(web3jService).isNotNull()
                .extracting("chainNodeAddress")
                .isEqualTo(RPC_DEFAULT_URL);
    }

    @Test
    void shouldCreateInstanceWithOverridenNodeAddress() {
        when(workerConfigurationService.getOverrideBlockchainNodeAddress()).thenReturn(RPC_OVERRIDE_URL);
        final Web3jService web3jService = new Web3jService(configServerConfigurationService, workerConfigurationService);
        assertThat(web3jService).isNotNull()
                .extracting("chainNodeAddress")
                .isEqualTo(RPC_OVERRIDE_URL);
    }
}
