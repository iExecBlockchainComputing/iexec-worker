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

package com.iexec.worker.feign.config;

import com.iexec.worker.config.WorkerConfigurationService;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    private final WorkerConfigurationService workerConfService;

    public RestTemplateConfig(WorkerConfigurationService workerConfService) {
        this.workerConfService = workerConfService;
    }

    @Bean
    public RestTemplate restTemplate() {
        final CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setProxy(getProxy())
                .build();
        final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(httpClient);
        return new RestTemplate(factory);
    }

    /*
     * TODO
     * Set multiple proxies
     * Use HttpRoutePlanner to support both http & https proxies at the same time
     * https://stackoverflow.com/a/34432952
     * */
    private HttpHost getProxy() {
        final String httpsProxyHost = workerConfService.getHttpsProxyHost();
        final Integer httpsProxyPort = workerConfService.getHttpsProxyPort();
        final String httpProxyHost = workerConfService.getHttpProxyHost();
        final Integer httpProxyPort = workerConfService.getHttpProxyPort();

        if (httpsProxyHost != null && httpsProxyPort != null) {
            return new HttpHost("https", httpsProxyHost, httpsProxyPort);
        } else if (httpProxyHost != null && httpProxyPort != null) {
            return new HttpHost("http", httpProxyHost, httpProxyPort);
        }
        return null;
    }
}
