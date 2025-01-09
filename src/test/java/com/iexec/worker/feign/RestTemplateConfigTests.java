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

package com.iexec.worker.feign;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.config.RestTemplateConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestTemplateConfigTests {

    @Mock
    private WorkerConfigurationService workerConfService;

    @InjectMocks
    private RestTemplateConfig restTemplateConfig;

    @Test
    void testRestTemplateWithHttpsProxy() {
        when(workerConfService.getHttpsProxyHost()).thenReturn("https-proxy.example.com");
        when(workerConfService.getHttpsProxyPort()).thenReturn(443);
        when(workerConfService.getHttpProxyHost()).thenReturn(null);
        when(workerConfService.getHttpProxyPort()).thenReturn(null);

        final RestTemplate restTemplate = restTemplateConfig.restTemplate();

        assertNotNull(restTemplate);
        final HttpComponentsClientHttpRequestFactory factory =
                (HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertNotNull(factory);
        final CloseableHttpClient httpClient = (CloseableHttpClient) factory.getHttpClient();
        assertNotNull(httpClient);
    }

    @Test
    void testRestTemplateWithHttpProxy() {
        when(workerConfService.getHttpsProxyHost()).thenReturn(null);
        when(workerConfService.getHttpsProxyPort()).thenReturn(null);
        when(workerConfService.getHttpProxyHost()).thenReturn("http-proxy.example.com");
        when(workerConfService.getHttpProxyPort()).thenReturn(8080);

        final RestTemplate restTemplate = restTemplateConfig.restTemplate();

        assertNotNull(restTemplate);
        final HttpComponentsClientHttpRequestFactory factory =
                (HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertNotNull(factory);
        final CloseableHttpClient httpClient = (CloseableHttpClient) factory.getHttpClient();
        assertNotNull(httpClient);
    }

    @Test
    void testRestTemplateNoProxy() {
        when(workerConfService.getHttpsProxyHost()).thenReturn(null);
        when(workerConfService.getHttpsProxyPort()).thenReturn(null);
        when(workerConfService.getHttpProxyHost()).thenReturn(null);
        when(workerConfService.getHttpProxyPort()).thenReturn(null);

        final RestTemplate restTemplate = restTemplateConfig.restTemplate();

        assertNotNull(restTemplate);
        final HttpComponentsClientHttpRequestFactory factory =
                (HttpComponentsClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertNotNull(factory);
        final CloseableHttpClient httpClient = (CloseableHttpClient) factory.getHttpClient();
        assertNotNull(httpClient);
    }
}
