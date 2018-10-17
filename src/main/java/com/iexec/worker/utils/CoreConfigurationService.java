package com.iexec.worker.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;

@Service
public class CoreConfigurationService {

    @Value("${core.host}")
    private String coreHost;

    @Value("${core.port}")
    private String corePort;

    private URL url;

    @PostConstruct
    public void run() throws MalformedURLException {
        url = new URL("http://" + coreHost + ":" + corePort);
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort();
    }
}
