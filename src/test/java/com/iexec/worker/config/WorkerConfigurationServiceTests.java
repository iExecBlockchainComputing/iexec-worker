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

package com.iexec.worker.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerConfigurationServiceTests {
    private static final String WORKER_WALLET_ADDRESS = "0x2D29bfBEc903479fe4Ba991918bAB99B494f2bEf";

    // region Constructor
    @Test
    void shouldConstructInstance() {
        final WorkerConfigurationService workerConfigurationService = new WorkerConfigurationService(WORKER_WALLET_ADDRESS);
        assertThat(workerConfigurationService).isNotNull()
                .extracting(WorkerConfigurationService::getWorkerWalletAddress).isEqualTo(WORKER_WALLET_ADDRESS);
    }
    // endregion
}
