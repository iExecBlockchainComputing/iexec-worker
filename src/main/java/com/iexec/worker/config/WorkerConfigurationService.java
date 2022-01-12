/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.config;

import com.iexec.common.utils.IexecFileHelper;
import com.iexec.worker.chain.CredentialsService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.io.File;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;

@Service
public class WorkerConfigurationService {

    private final CredentialsService credentialsService;

    @Value("${worker.name}")
    private String workerName;

    @Value("${worker.worker-base-dir}")
    private String workerBaseDir;

    @Value("${worker.override-available-cpu-count}")
    private Integer overrideAvailableCpuCount;

    @Value("${worker.gpu-enabled}")
    private boolean isGpuEnabled;

    @Value("${worker.gas-price-multiplier}")
    @Getter
    private float gasPriceMultiplier;

    @Value("${worker.gas-price-cap}")
    @Getter
    private long gasPriceCap;

    @Value("${worker.override-blockchain-node-address}")
    @Getter
    private String overrideBlockchainNodeAddress;

    @Value("${worker.developer-logger-enabled}")
    @Getter
    private boolean developerLoggerEnabled;

    @Value("${worker.tee-compute-max-heap-size-gb}")
    @Getter
    private int teeComputeMaxHeapSizeGb;

    @Value("${worker.docker-network-name}")
    @Getter
    private String dockerNetworkName;

    public WorkerConfigurationService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    @PostConstruct
    private void postConstruct() {
        if (overrideAvailableCpuCount != null && overrideAvailableCpuCount <= 0) {
            throw new IllegalArgumentException(
                    "Override available CPU count must not be less or equal to 0");
        }
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

    /**
     * Get path to input folder on the host side.
     * <p>
     * Expected: workerBaseDir/chainTaskId/input
     */
    public String getTaskInputDir(String chainTaskId) {
        return getTaskBaseDir(chainTaskId) + IexecFileHelper.SLASH_INPUT;
    }

    /**
     * Get path to output folder on the host side.
     * <p>
     * Expected: workerBaseDir/chainTaskId/output
     */
    public String getTaskOutputDir(String chainTaskId) {
        return getTaskBaseDir(chainTaskId) + IexecFileHelper.SLASH_OUTPUT;
    }

    /**
     * Get path to output folder inside the container.
     * <p>
     * Expected: workerBaseDir/chainTaskId/output/iexec_in
     */
    public String getTaskIexecOutDir(String chainTaskId) {
        return getTaskOutputDir(chainTaskId) + IexecFileHelper.SLASH_IEXEC_OUT;
    }

    public String getOS() {
        return System.getProperty("os.name").trim();
    }

    public String getCPU() {
        return System.getProperty("os.arch");
    }

    /**
     * Get the number of CPUs dedicated to the worker.
     * 
     * @return number of CPUs set by the worker admin if defined, otherwise
     * get max(numberOfJvmCpus -1, 1).
     */
    public int getCpuCount() {
        int defaultAvailableCpuCount = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        if (overrideAvailableCpuCount == null) {
            return defaultAvailableCpuCount;
        }
        return overrideAvailableCpuCount;
    }

    public int getMemorySize() {
        com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) getOperatingSystemMXBean();
        return Long.valueOf(os.getTotalPhysicalMemorySize() / (1024 * 1024 * 1024)).intValue();//in GB
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
