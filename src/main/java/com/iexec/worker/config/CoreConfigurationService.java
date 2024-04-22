/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;

@Service
public class CoreConfigurationService {

    @Value("${core.protocol}")
    private String coreProtocol;

    @Value("${core.host}")
    private String coreHost;

    @Value("${core.port}")
    private String corePort;

    private URL url;

    private String coreSessionId;

    @PostConstruct
    public void run() throws MalformedURLException {
        url = new URL(coreProtocol, coreHost, Integer.parseInt(corePort), "");
    }

    public String getUrl() {
        return url.toString();
    }

    public String getProtocol() {
        return url.getProtocol();
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort();
    }

    public String getCoreSessionId() {
        return coreSessionId;
    }

    public void setCoreSessionId(String coreSessionId) {
        this.coreSessionId = coreSessionId;
    }

    @Bean
    SchedulerClient schedulerClient() {
        return SchedulerClientBuilder.getInstance(Logger.Level.FULL, url.toString());
    }
}
