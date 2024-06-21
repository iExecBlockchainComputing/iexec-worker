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

package com.iexec.worker.config;

import com.iexec.core.api.SchedulerClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class SchedulerConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void shouldCreateBeanInstance() {
        runner.withPropertyValues("core.protocol=http", "core.host=localhost", "core.port=13000", "core.poolAddress=0x365E7BABAa85eC61Dffe5b520763062e6C29dA27")
                .withConfiguration(UserConfigurations.of(SchedulerConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulerClient.class);
                    assertThat(((SchedulerConfiguration) context.getBean("schedulerConfiguration"))
                            .getWorkerPoolAddress())
                            .isEqualTo("0x365E7BABAa85eC61Dffe5b520763062e6C29dA27");
                });
    }
}
