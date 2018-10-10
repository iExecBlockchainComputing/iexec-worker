package com.iexec.worker.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;

@Service
public class CoreConfigurationService {

    @Value("${core.address}")
    private String coreAddress;

    private URL url;

    @PostConstruct
    public void run() throws MalformedURLException {
        url = new URL(coreAddress);
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort();
    }
}
