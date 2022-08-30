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

package com.iexec.worker.config;

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.blockchain.api.BlockchainAdapterApiClientBuilder;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.resultproxy.api.ResultProxyClient;
import com.iexec.resultproxy.api.ResultProxyClientBuilder;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientBuilder;
import com.iexec.worker.feign.CustomCoreFeignClient;
import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class PublicConfigurationService {

    private final PublicConfiguration publicConfiguration;

    public PublicConfigurationService(CustomCoreFeignClient customCoreFeignClient) {
        this.publicConfiguration = customCoreFeignClient.getPublicConfiguration();
    }

    public PublicConfiguration getPublicConfiguration() {
        return publicConfiguration;
    }

    public String getBlockchainAdapterUrl() {
        return publicConfiguration.getBlockchainAdapterUrl();
    }

    public String getWorkerPoolAddress() {
        return publicConfiguration.getWorkerPoolAddress();
    }

    public String getSchedulerPublicAddress() {
        return publicConfiguration.getSchedulerPublicAddress();
    }

    public long getAskForReplicatePeriod() {
        return publicConfiguration.getAskForReplicatePeriod();
    }

    public String getResultRepositoryURL() {
        return publicConfiguration.getResultRepositoryURL();
    }

    public String getSmsURL() {
        return publicConfiguration.getSmsURL();
    }

    public String getRequiredWorkerVersion() {
        return publicConfiguration.getRequiredWorkerVersion();
    }

    @Bean
    public BlockchainAdapterApiClient blockchainAdapterApiClient() {
        return BlockchainAdapterApiClientBuilder.getInstance(Logger.Level.NONE, getBlockchainAdapterUrl());
    }

    @Bean
    public ResultProxyClient resultProxyClient() {
        return ResultProxyClientBuilder.getInstance(Logger.Level.NONE, getResultRepositoryURL());
    }

    @Bean
    public SmsClient smsClient() {
        return SmsClientBuilder.getInstance(Logger.Level.NONE, getSmsURL());
    }
}
