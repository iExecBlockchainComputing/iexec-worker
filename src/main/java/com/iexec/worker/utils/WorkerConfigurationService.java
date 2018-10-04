package com.iexec.worker.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WorkerConfigurationService {

    @Value("${worker.name}")
    private String workerName;

    @Value("${worker.volumeName}")
    private String workerVolumeName;

    @Value("${worker.localPath}")
    private String localPath;

    public String getWorkerName() {
        return workerName;
    }

    public String getWorkerVolumeName() {
        return workerVolumeName;
    }

    public String getLocalPath() {
        return localPath;
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
