/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.config.ConfigServerClient;
import com.iexec.commons.poco.chain.SignerService;
import com.iexec.worker.config.ConfigServerConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.SchedulerConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.crypto.WalletUtils;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class WalletConfigurationTest {
    private static final int WIREMOCK_PORT = 8080;

    private final ApplicationContextRunner runner = new ApplicationContextRunner();
    @TempDir
    private File tempWalletDir;

    @Container
    static final GenericContainer<?> wmServer = new GenericContainer<>("wiremock/wiremock:3.3.1")
            .withClasspathResourceMapping("wiremock", "/home/wiremock", BindMode.READ_ONLY)
            .withExposedPorts(WIREMOCK_PORT);

    @Test
    void shouldCreateBeans() throws Exception {
        final String tempWalletName = WalletUtils.generateFullNewWalletFile("changeit", tempWalletDir);
        final String tempWalletPath = tempWalletDir.getAbsolutePath() + File.separator + tempWalletName;
        runner.withPropertyValues("core.url=http://localhost:" + wmServer.getMappedPort(WIREMOCK_PORT),
                        "worker.name=worker", "worker.worker-base-dir=/tmp", "worker.override-available-cpu-count=",
                        "worker.gpu-enabled=false", "worker.gas-price-multiplier=1.0", "worker.gas-price-cap=22000000000",
                        "worker.override-blockchain-node-address=", "worker.developer-logger-enabled=true",
                        "worker.tee-compute-max-heap-size-gb=8", "worker.docker-network-name=iexec-worker-net")
                .withBean(ConfigServerConfigurationService.class)
                .withBean(IexecHubService.class)
                .withBean(PublicConfigurationService.class)
                .withBean(WalletConfiguration.class, tempWalletPath, "changeit")
                .withBean(Web3jService.class)
                .withBean(WorkerConfigurationService.class)
                .withUserConfiguration(SchedulerConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(ConfigServerClient.class)
                        .hasSingleBean(ConfigServerConfigurationService.class)
                        .hasSingleBean(IexecHubService.class)
                        .hasSingleBean(PublicConfigurationService.class)
                        .hasSingleBean(SchedulerConfiguration.class)
                        .hasSingleBean(SignerService.class)
                        .hasSingleBean(WalletConfiguration.class)
                        .hasSingleBean(Web3jService.class)
                        .hasSingleBean(WorkerConfigurationService.class));
    }
}
