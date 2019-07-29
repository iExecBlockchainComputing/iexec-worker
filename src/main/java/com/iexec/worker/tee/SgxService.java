package com.iexec.worker.tee;

import java.io.File;

import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.docker.DockerExecutionConfig;
import com.iexec.worker.utils.LoggingUtils;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SgxService {

    public static final String SGX_DEVICE_PATH = "/dev/isgx";
    public static final String SGX_CGROUP_PERMISSIONS = "rwm";
    private static final String SGX_DRIVER_PATH = "/sys/module/isgx/version";

    private CustomDockerClient customDockerClient;

    public SgxService(CustomDockerClient customDockerClient) {
        this.customDockerClient = customDockerClient;
    }

    public boolean isSgxEnabled() {
        boolean isSgxDriverFound = new File(SGX_DRIVER_PATH).exists();

        if (!isSgxDriverFound) {
            log.debug("SGX driver not found");
            return false;
        }

        boolean isSgxDeviceFound = isSgxDeviceFound();

        if (!isSgxDeviceFound) {
            String message = "SGX driver is installed but no SGX device found. Please check if SGX is enabled";
            LoggingUtils.printHighlightedMessage(message);
            return false;
        }

        return true;
    }

    public boolean isSgxDeviceFound() {
        String cmd = "ls /dev/isgx >/dev/null 2>1 && echo true || echo false";

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId("sgxCheck")
                .imageUri("alpine:lates")
                .cmd(cmd.split(" "))
                .containerName("sgxCheck")
                .maxExecutionTime(300) // 5min
                .build();

        String stdout = customDockerClient.runSgxCheckContainer(dockerExecutionConfig);
        return stdout != null && stdout.equals("true") ? true : false;
    }
}