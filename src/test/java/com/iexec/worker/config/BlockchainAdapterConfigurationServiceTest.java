/*
 * Copyright 2021-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.config;

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.common.config.PublicChainConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BlockchainAdapterConfigurationServiceTest {
    private static final Integer CHAIN_ID = 0;
    private static final boolean IS_SIDECHAIN = true;
    private static final String NODE_ADDRESS = "https://node";
    private static final String HUB_ADDRESS = "0x2";
    private static final Duration BLOCK_TIME = Duration.ofSeconds(1);

    @Mock
    private BlockchainAdapterApiClient blockchainAdapterClient;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldGetBlockTime() {
        when(blockchainAdapterClient.getPublicChainConfig()).thenReturn(
                PublicChainConfig.builder()
                        .chainId(CHAIN_ID)
                        .sidechain(IS_SIDECHAIN)
                        .chainNodeUrl(NODE_ADDRESS)
                        .iexecHubContractAddress(HUB_ADDRESS)
                        .blockTime(BLOCK_TIME)
                        .build()
        );

        BlockchainAdapterConfigurationService blockchainAdapterConfigurationService =
                new BlockchainAdapterConfigurationService(blockchainAdapterClient);

        assertThat(blockchainAdapterConfigurationService.getChainId())
                .isEqualTo(CHAIN_ID);
        assertThat(blockchainAdapterConfigurationService.isSidechain())
                .isEqualTo(IS_SIDECHAIN);
        assertThat(blockchainAdapterConfigurationService.getChainNodeUrl())
                .isEqualTo(NODE_ADDRESS);
        assertThat(blockchainAdapterConfigurationService.getIexecHubContractAddress())
                .isEqualTo(HUB_ADDRESS);
        assertThat(blockchainAdapterConfigurationService.getBlockTime())
                .isEqualTo(BLOCK_TIME);
    }
}