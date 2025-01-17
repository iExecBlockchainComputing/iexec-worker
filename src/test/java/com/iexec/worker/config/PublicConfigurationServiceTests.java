/*
 * Copyright 2024-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.core.api.SchedulerClient;
import com.iexec.core.config.PublicConfiguration;
import com.iexec.resultproxy.api.ResultProxyClientBuilder;
import feign.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicConfigurationServiceTests {

    @Mock
    private SchedulerClient schedulerClient;

    @Test
    void shouldBeOK() {
        when(schedulerClient.getPublicConfiguration()).thenReturn(
                PublicConfiguration.builder()
                        .configServerUrl("http://localhost:8888")
                        .resultRepositoryURL("http://localhost:13300")
                        .requiredWorkerVersion("v8")
                        .schedulerPublicAddress(("http://localhost:1300"))
                        .build()
        );

        final PublicConfigurationService publicConfigurationService = new PublicConfigurationService(schedulerClient);

        assertAll(
                () -> assertThat(publicConfigurationService).isNotNull(),
                () -> assertThat(publicConfigurationService.getSchedulerPublicAddress()).isEqualTo("http://localhost:1300"),
                () -> assertThat(publicConfigurationService.getRequiredWorkerVersion()).isEqualTo("v8"),
                () -> assertThat(publicConfigurationService.configServerClient()).isNotNull(),
                () -> assertThat(publicConfigurationService.createResultProxyClientFromURL(null)).isNotNull(),
                () -> assertThat(publicConfigurationService.createResultProxyClientFromURL(""))
                        .isEqualTo(ResultProxyClientBuilder.getInstance(Logger.Level.NONE, "http://localhost:13300")),
                () -> assertThat(publicConfigurationService.createResultProxyClientFromURL("https://www.result-proxy-repo.iex.ec"))
                        .isEqualTo(ResultProxyClientBuilder.getInstance(Logger.Level.NONE, "https://www.result-proxy-repo.iex.ec"))
        );
    }
}
