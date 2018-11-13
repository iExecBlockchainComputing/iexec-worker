package com.iexec.worker.utils;

import com.iexec.worker.chain.CredentialsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkerConfigurationService {

    private CredentialsService credentialsService;

    @Value("${worker.name}")
    private String workerName;

    @Value("${worker.resultBaseDir}")
    private String resultBaseDir;

    public WorkerConfigurationService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWorkerWalletAddress(){
        return credentialsService.getCredentials().getAddress();
    }

    public String getResultBaseDir() {
        return resultBaseDir + "/" + workerName;
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
}
