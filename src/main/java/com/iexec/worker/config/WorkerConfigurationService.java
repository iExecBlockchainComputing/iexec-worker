package com.iexec.worker.config;

import com.iexec.worker.chain.CredentialsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;

@Service
public class WorkerConfigurationService {

    private CredentialsService credentialsService;

    @Value("${worker.name}")
    private String workerName;

    @Value("${worker.resultBaseDir}")
    private String resultBaseDir;

    @Value("${worker.gasPriceMultiplier}")
    private float gasPriceMultiplier;

    @Value("${worker.gasPriceCap}")
    private long gasPriceCap;

    @Value("${worker.teeEnabled}")
    private boolean isTeeEnabled;

    @Value("${worker.overrideBlockchainNodeAddress}")
    private String overrideBlockchainNodeAddress;

    @Value("${worker.httpProxy.host}")
    private String httpProxyHost;

    @Value("${worker.httpProxy.port}")
    private Integer httpProxyPort;

    public WorkerConfigurationService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWorkerWalletAddress() {
        return credentialsService.getCredentials().getAddress();
    }

    public String getResultBaseDir() {
        return resultBaseDir + File.separator + workerName;
    }

    public String getOS() {
        return System.getProperty("os.name").trim();
    }

    public String getCPU() {
        return System.getProperty("os.arch");
    }

    public int getNbCPU() {
        return Runtime.getRuntime().availableProcessors();
    }

    public int getMemorySize() {
        com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) getOperatingSystemMXBean();
        return new Long(os.getTotalPhysicalMemorySize() / (1024 * 1024 * 1024)).intValue();//in GB
    }

    public float getGasPriceMultiplier() {
        return gasPriceMultiplier;
    }

    public long getGasPriceCap() {
        return gasPriceCap;
    }

    public boolean isTeeEnabled() {
        return isTeeEnabled;
    }

    public String getOverrideBlockchainNodeAddress() {
        return overrideBlockchainNodeAddress;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }
}
