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

package com.iexec.worker.config;

import org.junit.jupiter.api.Test;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;
import static org.assertj.core.api.Assertions.assertThat;

class WorkerConfigurationServiceTests {
    private final WorkerConfigurationService workerConfiguration = new WorkerConfigurationService();

    @Test
    void shouldGetDefaultCpuCount() {
        final int defaultAvailableCpuCount = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        assertThat(workerConfiguration.getCpuCount()).isEqualTo(defaultAvailableCpuCount);
    }

    @Test
    void shouldGetMemorySize() {
        final com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) getOperatingSystemMXBean();
        assertThat(workerConfiguration.getMemorySize()).isEqualTo((int) os.getTotalMemorySize() / (1024 * 1024 * 1024));
    }
}
