package com.iexec.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;

@Service
public class CoreConfigurationService {

    @Value("${core.protocol}")
    private String coreProtocol;

    @Value("${core.host}")
    private String coreHost;

    @Value("${core.port}")
    private String corePort;

    private URL url;

    private String coreSessionId;

    @PostConstruct
    public void run() throws MalformedURLException {
        url = new URL(coreProtocol, coreHost, Integer.parseInt(corePort), "");
    }

    public String getUrl() {
        return url.toString();
    }

    public String getProtocol() {
        return url.getProtocol();
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort();
    }

    public String getCoreSessionId() {
        return coreSessionId;
    }

    public void setCoreSessionId(String coreSessionId) {
        this.coreSessionId = coreSessionId;
    }
}
