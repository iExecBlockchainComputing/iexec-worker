package com.iexec.worker.sgx;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.iexec.worker.compute.DockerService;
import com.iexec.worker.compute.DockerCompute;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.LoggingUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SgxService {

    public static final String SGX_DEVICE_PATH = "/dev/isgx";
    public static final String SGX_CGROUP_PERMISSIONS = "rwm";
    private static final String SGX_DRIVER_PATH = "/sys/module/isgx/version";

    private boolean isSgxSupported;

    private DockerService dockerService;
    private WorkerConfigurationService workerConfigService;

    public SgxService(DockerService dockerService,
                      WorkerConfigurationService workerConfigService,
                      @Value("${debug.forceTeeDisabled}") boolean forceTeeDisabled) {
        this.dockerService = dockerService;
        this.workerConfigService = workerConfigService;
        isSgxSupported = forceTeeDisabled ? false : isSgxSupported();
    }

    public boolean isSgxEnabled() {
        return isSgxSupported;
    }

    private boolean isSgxSupported() {
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
        String chainTaskId = "sgx-check";
        // "wallet-address-sgx-check" as containerName to avoid naming conflict
        // when running multiple workers on the same machine.
        String containerName = workerConfigService.getWorkerWalletAddress() + "-sgx-check";
        String alpineLatest = "alpine:latest";
        String cmd = "find /dev -name isgx -exec echo true ;";

        Map<String, String> bindPaths = new HashMap<>();
        bindPaths.put("/dev", "/dev");

        DockerCompute dockerCompute = DockerCompute.builder()
                .chainTaskId(chainTaskId)
                .containerName(containerName)
                .imageUri(alpineLatest)
                .cmd(cmd)
                .maxExecutionTime(60000) // 1 min
                .bindPaths(bindPaths)
                .build();

        if (!dockerService.pullImage(chainTaskId, alpineLatest)) {
            return false;
        }

        Optional<String> oStdout = dockerService.run(dockerCompute);
        if (oStdout.isEmpty()) {
            log.error("Failed to check SGX device, will continue without TEE support");
            return false;
        }

        String stdout = oStdout.get().trim();
        return (stdout != null && stdout.equals("true"));
    }
}