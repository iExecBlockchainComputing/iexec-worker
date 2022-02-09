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

package com.iexec.worker.sgx;

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.sgx.SgxDriverMode;
import com.iexec.common.utils.SgxUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class SgxService {

    private final WorkerConfigurationService workerConfigService;
    private final ApplicationContext context;
    private final DockerService dockerService;
    private final SgxDriverMode sgxDriverMode;

    private boolean sgxEnabled;

    public SgxService(
            WorkerConfigurationService workerConfigService,
            ApplicationContext context,
            DockerService dockerService,
            @Value("${tee.sgx.driver-mode}") SgxDriverMode sgxDriverMode
    ) {
        this.workerConfigService = workerConfigService;
        this.context = context;
        this.dockerService = dockerService;
        this.sgxDriverMode = sgxDriverMode;
    }

    @PostConstruct
    void init() {
        boolean sgxSupported = isSgxSupported(sgxDriverMode);
        final boolean driverModeNotNone = SgxDriverMode.isDriverModeNotNone(sgxDriverMode);
        if (!sgxSupported && driverModeNotNone) {
            this.sgxEnabled = false;
            log.error("SGX required but not supported by worker. Shutting down. " +
                    "[sgxDriverMode: {}]", sgxDriverMode);
            SpringApplication.exit(context, () -> 1);
            return;
        }

        this.sgxEnabled = driverModeNotNone;
    }

    public SgxDriverMode getSgxDriverMode() {
        return sgxDriverMode;
    }

    public boolean isSgxEnabled() {
        return sgxEnabled;
    }

    private boolean isSgxSupported(SgxDriverMode sgxDriverMode) {
        log.info("Checking SGX support");
        if (sgxDriverMode == SgxDriverMode.LEGACY) {
            boolean isSgxDriverFound = new File(SgxUtils.SGX_DRIVER_PATH).exists();
            if (!isSgxDriverFound) {
                log.error("SGX driver not found");
                return false;
            }
        }
        if (!isSgxDevicePresent(sgxDriverMode)) {
            log.error("SGX driver is installed but no SGX device was found " +
                    "(SGX not enabled?)");
            return false;
        }
        log.info("SGX is enabled");
        return true;
    }

    private boolean isSgxDevicePresent(SgxDriverMode sgxDriverMode) {
        // "wallet-address-sgx-check" as containerName to avoid naming conflict
        // when running multiple workers on the same machine.
        String containerName = workerConfigService.getWorkerWalletAddress() + "-sgx-check";
        String alpineLatest = "alpine:latest";

        final Path[] devices = sgxDriverMode.getDevices();
        // The following will count SGX devices
        // related to selected SGX driver mode in `/dev`.
        final String cmd = String.format("/bin/sh -c 'echo $(ls /dev | grep -w %s)'",
                Arrays.stream(devices)
                        .map(devicePath -> "-e \"" + devicePath.getFileName() + "\"")
                        .collect(Collectors.joining(" ")));

        if (!dockerService.getClient().pullImage(alpineLatest)) {
            log.error("Failed to pull image for sgx check");
            return false;
        }

        final List<String> devicesBind = Arrays.stream(devices)
                .map(devicePath -> devicePath + ":" + devicePath)
                .collect(Collectors.toList());

        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .imageUri(alpineLatest)
                .cmd(cmd)
                .maxExecutionTime(60000) // 1 min
                .binds(devicesBind)
                .build();


        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Failed to check SGX device.");
            return false;
        }

        // Check retrieved devices are those we were looking for.
        String stdout = dockerRunResponse.getStdout().trim();
        return Set.of(stdout.split("\\s"))
                .equals(Set.of(devices));
    }
}
