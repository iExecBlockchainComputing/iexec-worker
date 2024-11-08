/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeDurationsConfigTests {
    private static final int WINDOW_SIZE = 1_000;
    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private ComputeDurationsConfig computeDurationsConfig;

    @BeforeEach
    void init() {
        computeDurationsConfig = new ComputeDurationsConfig(WINDOW_SIZE);
    }

    // region Constructor
    @Test
    void shouldConstructConfigWithWindowSize() {
        final Object windowSize = ReflectionTestUtils.getField(computeDurationsConfig, "windowSize");
        assertThat(windowSize).isEqualTo(WINDOW_SIZE);
    }
    // endregion

    // region preComputeDurationService
    @Test
    void shouldConstructPreComputeDurationService() {
        final ComputeDurationsService preComputeDurationsService =
                computeDurationsConfig.preComputeDurationsService(new SimpleMeterRegistry(), WALLET_ADDRESS);
        assertThat(preComputeDurationsService).isNotNull();
    }
    // endregion

    // region appComputeDurationService
    @Test
    void shouldConstructAppComputeDurationService() {
        final ComputeDurationsService appComputeDurationsService =
                computeDurationsConfig.appComputeDurationsService(new SimpleMeterRegistry(), WALLET_ADDRESS);
        assertThat(appComputeDurationsService).isNotNull();
    }
    // endregion

    // region postComputeDurationService
    @Test
    void shouldConstructPostComputeDurationService() {
        final ComputeDurationsService postComputeDurationsService =
                computeDurationsConfig.postComputeDurationsService(new SimpleMeterRegistry(), WALLET_ADDRESS);
        assertThat(postComputeDurationsService).isNotNull();
    }
    // endregion
}
