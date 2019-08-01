package com.iexec.worker.feign;

import java.io.File;

import javax.net.ssl.SSLContext;

import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class SslService {

    /*
    * The truststore should contains the iexec-core certificate when iexec-core is using a self-signed certificate (dev)
    *
    */

    @Value("${http.client.ssl.trust-store}")
    private String truststore;

    @Value("${http.client.ssl.trust-store-password}")
    private String truststorePassword;


    public SSLContext getSslContext() {
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
}