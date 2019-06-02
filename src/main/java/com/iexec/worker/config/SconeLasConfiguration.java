package com.iexec.worker.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SconeLasConfiguration {

    @Value("${scone.las.host}")
    private String host;

    @Value("${scone.las.port}")
    private String port;

    public String getURL() {
        return host + ":" + port;
    }
}