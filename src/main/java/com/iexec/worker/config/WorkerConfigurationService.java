package com.iexec.worker.config;

import com.iexec.common.utils.FileHelper;
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

    @Value("${worker.workerBaseDir}")
    private String workerBaseDir;

    @Value("${worker.gpuEnabled}")
    private boolean isGpuEnabled;

    @Value("${worker.gasPriceMultiplier}")
    private float gasPriceMultiplier;

    @Value("${worker.gasPriceCap}")
    private long gasPriceCap;

    @Value("${worker.overrideBlockchainNodeAddress}")
    private String overrideBlockchainNodeAddress;

    @Value("${worker.developerLoggerEnabled}")
    private boolean developerLoggerEnabled;

    public WorkerConfigurationService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String getWorkerWalletAddress() {
        return credentialsService.getCredentials().getAddress();
    }

    public boolean isGpuEnabled() {
        return isGpuEnabled;
    }

    public String getWorkerBaseDir() {
        return workerBaseDir + File.separator + workerName;
    }

    public String getTaskBaseDir(String chainTaskId) {
        return getWorkerBaseDir() + File.separator + chainTaskId;
    }

    public String getTaskInputDir(String chainTaskId) {
        return getTaskBaseDir(chainTaskId) + FileHelper.SLASH_INPUT;
    }

    public String getTaskOutputDir(String chainTaskId) {
        return getTaskBaseDir(chainTaskId) + FileHelper.SLASH_OUTPUT;
    }

    public String getTaskResultDir(String chainTaskId) {
        return getTaskBaseDir(chainTaskId) + FileHelper.SLASH_RESULT;
    }

    public String getTaskIexecOutDir(String chainTaskId) {
        return getTaskResultDir(chainTaskId) + FileHelper.SLASH_IEXEC_OUT;
    }

    public String getDatasetSecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/input/dataset.secret
        return getTaskInputDir(chainTaskId) + File.separator + "dataset.secret";
    }

    public String getBeneficiarySecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/beneficiary.secret
        return getTaskBaseDir(chainTaskId) + File.separator + "beneficiary.secret";
    }

    public String getEnclaveSecretFilePath(String chainTaskId) {
        // /worker-base-dir/chainTaskId/enclave.secret
        return getTaskBaseDir(chainTaskId) + File.separator + "enclave.secret";
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
        return Long.valueOf(os.getTotalPhysicalMemorySize() / (1024 * 1024 * 1024)).intValue();//in GB
    }

    public float getGasPriceMultiplier() {
        return gasPriceMultiplier;
    }

    public long getGasPriceCap() {
        return gasPriceCap;
    }

    public String getOverrideBlockchainNodeAddress() {
        return overrideBlockchainNodeAddress;
    }

    public boolean isDeveloperLoggerEnabled() {
        return developerLoggerEnabled;
    }

    public String getHttpProxyHost() {
        return System.getProperty("http.proxyHost");
    }

    public Integer getHttpProxyPort() {
        String proxyPort = System.getProperty("http.proxyPort");
        return proxyPort != null && !proxyPort.isEmpty() ? Integer.valueOf(proxyPort) : null;
    }

    public String getHttpsProxyHost() {
        return System.getProperty("https.proxyHost");
    }

    public Integer getHttpsProxyPort() {
        String proxyPort = System.getProperty("https.proxyPort");
        return proxyPort != null && !proxyPort.isEmpty() ? Integer.valueOf(proxyPort) : null;
    }
}
