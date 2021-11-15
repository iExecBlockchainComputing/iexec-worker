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

package com.iexec.worker.config;

import com.iexec.common.config.PublicChainConfig;
import com.iexec.worker.feign.CustomBlockchainAdapterClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static org.web3j.protocol.core.JsonRpc2_0Web3j.DEFAULT_BLOCK_TIME;

@Slf4j
@Service
public class BlockchainAdapterConfigurationService {
    private final PublicChainConfig publicChainConfig;

    public BlockchainAdapterConfigurationService(CustomBlockchainAdapterClient customBlockchainAdapterClient) {
        this.publicChainConfig = customBlockchainAdapterClient.getPublicChainConfig();
        log.info("Received public chain config [{}]", this.publicChainConfig);

        if (publicChainConfig.getBlockTime() == null) {
            log.warn("Incorrect block time, using default [{}ms]", DEFAULT_BLOCK_TIME);
            publicChainConfig.setBlockTime(Duration.ofMillis(DEFAULT_BLOCK_TIME));
        }
    }

    /**
     * Retrieved when the {@link BlockchainAdapterConfigurationService} is built
     * so that it should be constant over time.
     * <br>
     * A restart is required to retrieve a fresh remote value.
     *
     * @return A {@link Duration} representing the block time of the blockchain.
     */
    public Duration getBlockTime() {
        return publicChainConfig.getBlockTime();
    }
}
