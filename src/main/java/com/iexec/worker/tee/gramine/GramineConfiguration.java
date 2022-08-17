package com.iexec.worker.tee.gramine;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GramineConfiguration {
    @Getter
    @Value("${gramine.sps.ssl-certs}")
    private String sslCertificates;
}
