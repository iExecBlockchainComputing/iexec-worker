package com.iexec.worker.feign;

import feign.Client;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;

@Configuration
public class FeignConfiguration {

    @Value("${http.client.ssl.trust-store}")
    private String truststore;
    @Value("${http.client.ssl.trust-store-password}")
    private String truststorePassword;

    private SSLSocketFactory getSocketFactory() {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(
                            new File(truststore),
                            truststorePassword.toCharArray()
                    ).build();
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Bean
    public Client feignClient() {
        return new Client.Default(getSocketFactory(), new NoopHostnameVerifier());
    }

}
