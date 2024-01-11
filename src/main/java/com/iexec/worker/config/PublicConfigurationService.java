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

package com.iexec.worker.config;

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.blockchain.api.BlockchainAdapterApiClientBuilder;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.resultproxy.api.ResultProxyClient;
import com.iexec.resultproxy.api.ResultProxyClientBuilder;
import com.iexec.worker.feign.CustomCoreFeignClient;
import feign.Logger;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Getter
@Service
public class PublicConfigurationService {

    private final PublicConfiguration publicConfiguration;

    public PublicConfigurationService(CustomCoreFeignClient customCoreFeignClient) {
        this.publicConfiguration = customCoreFeignClient.getPublicConfiguration();
    }

    public String getSchedulerPublicAddress() {
        return publicConfiguration.getSchedulerPublicAddress();
    }

    public String getRequiredWorkerVersion() {
        return publicConfiguration.getRequiredWorkerVersion();
    }

    @Bean
    public BlockchainAdapterApiClient blockchainAdapterApiClient() {
        return BlockchainAdapterApiClientBuilder.getInstance(
                Logger.Level.NONE,
                publicConfiguration.getBlockchainAdapterUrl());
    }

    @Bean
    public ResultProxyClient resultProxyClient() {
        return ResultProxyClientBuilder.getInstance(
                Logger.Level.NONE,
                publicConfiguration.getResultRepositoryURL());
    }
}
