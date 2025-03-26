/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.core.api.SchedulerClientBuilder;
import feign.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotEmpty;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.annotation.Validated;

@Value
@Validated
@ConfigurationProperties(prefix = "core")
public class SchedulerConfiguration {

    @URL(message = "URL must be a valid URL")
    @NotEmpty(message = "URL must not be empty")
    String url;
    String poolAddress;

    @PostConstruct
    private void postConstruct() {
        if (StringUtils.isEmpty(poolAddress) || poolAddress.equalsIgnoreCase("0x0")) {
            throw new MissingConfigurationException(
                    "The workerpool address must be filled in");
        }
    }

    @Bean
    SchedulerClient schedulerClient() {
        return SchedulerClientBuilder.getInstance(Logger.Level.FULL, url);
    }
}
