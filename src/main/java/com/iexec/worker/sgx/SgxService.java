package com.iexec.worker.sgx;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.docker.DockerExecutionConfig;
import com.iexec.worker.docker.DockerExecutionResult;
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
        log.info("Checking SGX support");
        boolean isSgxDriverFound = new File(SGX_DRIVER_PATH).exists();

        if (!isSgxDriverFound) {
            log.info("SGX driver not found");
            return false;
        }

        boolean isSgxDeviceFound = isSgxDeviceFound();

        if (!isSgxDeviceFound) {
            String message = "SGX driver is installed but no SGX device found (SGX not enabled?)";
            message += " We'll continue without TEE support";
            LoggingUtils.printHighlightedMessage(message);
            return false;
        }

        log.info("SGX is enabled, worker can execute TEE tasks");
        return true;
    }

    private boolean isSgxDeviceFound() {
        String chainTaskId = "sgxCheck";
        String alpineLatest = "alpine:latest";
        String cmd = "find /dev -name isgx -exec echo true ;";

        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put("/dev", "/dev");

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                // don't add containerName here it creates conflict
                // when running multiple workers on the same machine
                .imageUri(alpineLatest)
                .cmd(cmd.split(" "))
                .maxExecutionTime(60000) // 1 min (in millis)
                .bindPaths(bindPaths)
                .build();

        customDockerClient.pullImage(chainTaskId, alpineLatest);
        DockerExecutionResult executionResult = customDockerClient.execute(dockerExecutionConfig);
        if (!executionResult.isSuccess()) {
            log.error("Failed to check SGX device, will continue without TEE support");
            return false;
        }

        String stdout = executionResult.getStdout().trim();
        return (stdout != null && stdout.equals("true")) ? true : false;
    }
}