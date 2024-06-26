/*
 * Copyright 2021-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.config.ConfigServerClient;
import com.iexec.common.config.PublicChainConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * This service retrieves a bunch of configuration values related to the chain.
 * They are retrieved only when the instance is built and never updated.
 * A restart is then needed to get fresh remote values.
 */
@Slf4j
@Service
public class ConfigServerConfigurationService {
    private final PublicChainConfig publicChainConfig;

    public ConfigServerConfigurationService(ConfigServerClient configServerClient) {
        this.publicChainConfig = configServerClient.getPublicChainConfig();
        if (publicChainConfig == null) {
            throw new MissingConfigurationException(
                    "Received public chain config is null; can't create ConfigServerConfigurationService");
        }

        log.info("Received public chain config [config:{}]", this.publicChainConfig);
    }

    public Integer getChainId() {
        return publicChainConfig.getChainId();
    }

    public boolean isSidechain() {
        return publicChainConfig.isSidechain();
    }

    public String getChainNodeUrl() {
        return publicChainConfig.getChainNodeUrl();
    }

    public String getIexecHubContractAddress() {
        return publicChainConfig.getIexecHubContractAddress();
    }

    public Duration getBlockTime() {
        return publicChainConfig.getBlockTime();
    }
}
