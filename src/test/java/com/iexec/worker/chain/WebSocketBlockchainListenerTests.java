/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.worker.TestApplication;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.iexec.worker.chain.WebSocketBlockchainListener.LATEST_BLOCK_METRIC_NAME;
import static com.iexec.worker.chain.WebSocketBlockchainListener.TX_COUNT_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class WebSocketBlockchainListenerTests {

    private static final String CHAIN_SVC_NAME = "chain";
    private static final int CHAIN_SVC_PORT = 8545;
    private static final String CORE_SVC_NAME = "core";
    private static final int CORE_SVC_PORT = 8080;

    @Container
    static ComposeContainer environment = new ComposeContainer(new File("docker-compose.yml"))
            .withExposedService(CHAIN_SVC_NAME, CHAIN_SVC_PORT, Wait.forListeningPort())
            .withExposedService(CORE_SVC_NAME, CORE_SVC_PORT, Wait.forListeningPort())
            .withPull(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        final String coreHost = environment.getServiceHost(CORE_SVC_NAME, CORE_SVC_PORT);
        final int corePort = environment.getServicePort(CORE_SVC_NAME, CORE_SVC_PORT);

        registry.add("core.url", () -> getServiceUrl(coreHost, corePort));
        registry.add("core.pool-address", () -> "0x1");
        registry.add("worker.override-blockchain-node-address", () -> getServiceUrl(
                environment.getServiceHost(CHAIN_SVC_NAME, CHAIN_SVC_PORT),
                environment.getServicePort(CHAIN_SVC_NAME, CHAIN_SVC_PORT)));
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private Web3jService web3jService;

    private static String getServiceUrl(String serviceHost, int servicePort) {
        return "http://" + serviceHost + ":" + servicePort;
    }

    @Test
    void shouldConnect() {
        await().atMost(10L, TimeUnit.SECONDS)
                .until(() -> Objects.requireNonNull(meterRegistry.find(LATEST_BLOCK_METRIC_NAME).gauge()).value() != 0.0);
        assertThat(meterRegistry.find(TX_COUNT_METRIC_NAME).tag("block", "latest").gauge())
                .isNotNull()
                .extracting(Gauge::value)
                .isEqualTo(0.0);
        assertThat(meterRegistry.find(TX_COUNT_METRIC_NAME).tag("block", "pending").gauge())
                .isNotNull()
                .extracting(Gauge::value)
                .isEqualTo(0.0);
        final Long latestBlockNumber = (long) Objects.requireNonNull(meterRegistry.find(LATEST_BLOCK_METRIC_NAME).gauge()).value();
        assertThat(latestBlockNumber).isEqualTo(web3jService.getLatestBlockNumber());
    }

}
