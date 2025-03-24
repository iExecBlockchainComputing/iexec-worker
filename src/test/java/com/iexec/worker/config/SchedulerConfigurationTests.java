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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class SchedulerConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void shouldCreateBeanInstance() {
        runner.withPropertyValues(
                        "core.url=http://localhost:13000",
                        "core.pool-address=0x365E7BABAa85eC61Dffe5b520763062e6C29dA27")
                .withConfiguration(UserConfigurations.of(SchedulerConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulerClient.class);
                    assertThat(context).getBean("schedulerConfiguration", SchedulerConfiguration.class)
                            .extracting("poolAddress")
                            .isEqualTo("0x365E7BABAa85eC61Dffe5b520763062e6C29dA27");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0x0"})
    void shouldFailedAndRaisedExceptionWhenPoolAddressIsInvalid(String poolAddress) {
        runner.withPropertyValues(
                        "core.url=http://localhost:13000",
                        "core.pool-address=" + poolAddress)
                .withConfiguration(UserConfigurations.of(SchedulerConfiguration.class))
                .run(context -> {
                    assertThatThrownBy(() -> context.getBean(SchedulerConfiguration.class))
                            .isInstanceOf(IllegalStateException.class)
                            .hasCauseInstanceOf(BeanCreationException.class)
                            .hasRootCauseMessage("The workerpool address must be filled in");
                });
    }

    @Test
    void shouldFailWhenUrlIsEmpty() {
        runner.withPropertyValues(
                        "core.url=",
                        "core.pool-address=0x365E7BABAa85eC61Dffe5b520763062e6C29dA27")
                .withUserConfiguration(SchedulerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanCreationException.class)
                            .hasMessageContaining("Error creating bean with name 'schedulerClient'");
                });
    }

    @Test
    void shouldPassWithValidUrl() {
        runner.withPropertyValues(
                        "core.url=http://localhost:8080",
                        "core.pool-address=0x365E7BABAa85eC61Dffe5b520763062e6C29dA27")
                .withConfiguration(UserConfigurations.of(SchedulerConfiguration.class))
                .run(context -> {
                    SchedulerConfiguration config = context.getBean(SchedulerConfiguration.class);
                    assertThat(config.getUrl()).isEqualTo("http://localhost:8080");
                });
    }
}
