/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.HostConfig;
import com.iexec.commons.containers.*;
import com.iexec.worker.docker.DockerService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SgxService {

    private final ApplicationContext context;
    private final DockerService dockerService;
    @Getter
    private final SgxDriverMode sgxDriverMode;
    private final String workerWalletAddress;
    @Getter
    private boolean sgxEnabled;

    public SgxService(
            ApplicationContext context,
            DockerService dockerService,
            @Value("${tee.sgx.driver-mode:NONE}") SgxDriverMode sgxDriverMode,
            String workerWalletAddress
    ) {
        this.context = context;
        this.dockerService = dockerService;
        this.sgxDriverMode = sgxDriverMode;
        this.workerWalletAddress = workerWalletAddress;
    }

    @PostConstruct
    void init() {
        sgxEnabled = SgxDriverMode.isDriverModeNotNone(sgxDriverMode);
        if (!sgxEnabled) {
            log.info("No SGX driver defined, skipping SGX check [sgxDriverMode:{}]", sgxDriverMode);
            return;
        }
        // SgxDriver.isDriverModeNotNone is always true when reaching this line
        // sgxEnabled becomes equal to isSgxSupported in this case
        sgxEnabled = isSgxSupported(sgxDriverMode);
        if (!sgxEnabled) {
            log.error("SGX required but not supported by worker. Shutting down. [sgxDriverMode:{}]", sgxDriverMode);
            SpringApplication.exit(context, () -> 1);
        }
    }

    public List<Device> getSgxDevices() {
        return Arrays.stream(sgxDriverMode.getDevices())
                .map(Device::parse)
                .collect(Collectors.toList());
    }

    private boolean isSgxSupported(SgxDriverMode sgxDriverMode) {
        log.info("Checking SGX support");
        if (sgxDriverMode == SgxDriverMode.LEGACY) {
            boolean isSgxDriverFound = new File(SgxUtils.LEGACY_SGX_DRIVER_PATH).exists();
            if (!isSgxDriverFound) {
                log.error("Legacy SGX driver not found");
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
        String containerName = workerWalletAddress + "-sgx-check";
        String alpineLatest = "alpine:latest";

        final String[] devices = sgxDriverMode.getDevices();
        // Check all required devices exists
        final String cmd = String.format("/bin/sh -c '%s'",
                Arrays.stream(devices)
                        .map(devicePath -> "test -e \"" + devicePath + "\"")
                        .collect(Collectors.joining(" && ")));

        if (!dockerService.getClient().pullImage(alpineLatest)) {
            log.error("Failed to pull image for sgx check");
            return false;
        }

        final List<Device> devicesBind = Arrays.stream(devices)
                .map(Device::parse)
                .collect(Collectors.toList());

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withDevices(devicesBind);
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .containerName(containerName)
                .imageUri(alpineLatest)
                .cmd(cmd)
                .maxExecutionTime(60000) // 1 min
                .build();


        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (dockerRunResponse.getFinalStatus() != DockerRunFinalStatus.SUCCESS) {
            log.error("Failed to check SGX device.");
            return false;
        }

        return true;
    }
}
