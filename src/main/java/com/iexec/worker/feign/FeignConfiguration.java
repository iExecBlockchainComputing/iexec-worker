package com.iexec.worker.feign;

import com.iexec.worker.config.WorkerConfigurationService;
import feign.Client;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;

@Configuration
public class FeignConfiguration {

    /*
    * The truststore should contains the iexec-core certificate when iexec-core is using a self-signed certificate (dev)
    * */
    @Value("${http.client.ssl.trust-store}")
    private String truststore;
    @Value("${http.client.ssl.trust-store-password}")
    private String truststorePassword;

    private WorkerConfigurationService workerConfService;

    public FeignConfiguration(WorkerConfigurationService workerConfService) {
        this.workerConfService = workerConfService;
    }

    private SSLContext getSslContext() {
        try {
            return new SSLContextBuilder()
                    .loadTrustMaterial(
                            new File(truststore),
                            truststorePassword.toCharArray()
                    ).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Bean
    public Client feignClient() {
        SSLSocketFactory socketFactory = null;
        if (getSslContext() != null) {
            socketFactory = getSslContext().getSocketFactory();
        }
        return new Client.Default(socketFactory, new NoopHostnameVerifier());
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
        SSLContext sslContext = getSslContext();
        if (sslContext != null) {
            clientBuilder.setSSLContext(sslContext);
        }
    }

}
