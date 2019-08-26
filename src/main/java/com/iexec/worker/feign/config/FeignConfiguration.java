package com.iexec.worker.feign.config;

import feign.Client;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

@Configuration
public class FeignConfiguration {

    private SslService sslService;

    public FeignConfiguration(SslService sslService) {
        this.sslService = sslService;
    }

    @Bean
    public Client feignClient() {
        SSLSocketFactory socketFactory = null;
        SSLContext sslContext = sslService.getSslContext();
        if (sslContext != null) {
            socketFactory = sslContext.getSocketFactory();
        }
        return new Client.Default(socketFactory, new NoopHostnameVerifier());
    }
}
