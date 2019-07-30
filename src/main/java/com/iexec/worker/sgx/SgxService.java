package com.iexec.worker.sgx;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
            String message = "SGX driver is installed but no SGX device found. SGX not enabled?\n";
            message += "We'll continue without TEE support";
            LoggingUtils.printHighlightedMessage(message);
            return false;
        }

        log.info("SGX is enabled, worker can execute TEE tasks");
        return true;
    }

    public boolean checkSgxDriver() {
        boolean isSgxDriverFound = new File(SGX_DRIVER_PATH).exists();

        if (!isSgxDriverFound) {
            log.debug("SGX driver not found");
            return false;
        }

        boolean isSgxDeviceFound = isSgxDeviceFound();

        if (!isSgxDeviceFound) {
            String message = "SGX driver is installed but no SGX device found. SGX not enabled?\n";
            message += "We'll continue without TEE support";
            LoggingUtils.printHighlightedMessage(message);
            return false;
        }

        log.info("SGX is enabled, worker can execute TEE tasks");
        return true;
    }

    public boolean isSgxDeviceFound() {
        String cmd = "ls /dev/isgx >/dev/null 2>1 && echo true || echo false";

        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put("/dev", "/dev");

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .imageUri("alpine:lates")
                .cmd(cmd.split(" "))
                .containerName("sgxCheck")
                .maxExecutionTime(300) // 5min
                .bindPaths(bindPaths)
                .build();

        String stdout = customDockerClient.execute(dockerExecutionConfig);
        if (stdout.isEmpty()) {
            log.error("Could not check SGX device, will run worker without SGX support");
            return false;
        }

        return stdout.equals("true") ? true : false;
    }
}