package com.iexec.worker.feign;

import javax.net.ssl.SSLContext;

import com.iexec.worker.config.WorkerConfigurationService;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


@Configuration
public class RestTemplateConfig {

    private WorkerConfigurationService workerConfService;
    private SslService sslService;

    public RestTemplateConfig(WorkerConfigurationService workerConfService,
                              SslService sslService) {
        this.workerConfService = workerConfService;
        this.sslService = sslService;
    }
    @Bean
    public RestTemplate restTemplate() {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        setProxy(clientBuilder);
        setSslContext(clientBuilder);
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(clientBuilder.build());
        return new RestTemplate(factory);
    }

    /*
    * TODO
    * Set multiple proxies
    * Use HttpRoutePlanner to support both http & https proxies at the same time
    * https://stackoverflow.com/a/34432952
    * */
    private void setProxy(HttpClientBuilder clientBuilder) {
        HttpHost proxy = null;
        if (workerConfService.getHttpsProxyHost() != null && workerConfService.getHttpsProxyPort() != null) {
            proxy = new HttpHost(workerConfService.getHttpsProxyHost(), workerConfService.getHttpsProxyPort(), "https");
        } else if (workerConfService.getHttpProxyHost() != null && workerConfService.getHttpProxyPort() != null) {
            proxy = new HttpHost(workerConfService.getHttpProxyHost(), workerConfService.getHttpProxyPort(), "http");
        }
        if (proxy != null){
            clientBuilder.setProxy(proxy);
        }
    }

    private void setSslContext(HttpClientBuilder clientBuilder) {
        SSLContext sslContext = sslService.getSslContext();
        if (sslContext != null) {
            clientBuilder.setSSLContext(sslContext);
        }
    }
}