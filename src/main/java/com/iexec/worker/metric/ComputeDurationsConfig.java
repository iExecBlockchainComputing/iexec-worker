/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.metric;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComputeDurationsConfig {
    private final int windowSize;

    public ComputeDurationsConfig(@Value("${metrics.window-size}") int windowSize) {
        this.windowSize = windowSize;
    }

    @Bean
    ComputeDurationsService preComputeDurationsService(MeterRegistry registry,
                                                       String workerWalletAddress) {
        return new ComputeDurationsService(registry, workerWalletAddress, "pre_compute", windowSize);
    }

    @Bean
    ComputeDurationsService appComputeDurationsService(MeterRegistry registry,
                                                       String workerWalletAddress) {
        return new ComputeDurationsService(registry, workerWalletAddress, "app_compute", windowSize);
    }

    @Bean
    ComputeDurationsService postComputeDurationsService(MeterRegistry registry,
                                                        String workerWalletAddress) {
        return new ComputeDurationsService(registry, workerWalletAddress, "post_compute", windowSize);
    }
}
