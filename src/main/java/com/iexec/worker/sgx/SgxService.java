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
import com.iexec.common.utils.SgxUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;


@Slf4j
@Service
public class SgxService {

    private final WorkerConfigurationService workerConfigService;
    private final DockerService dockerService;
    private final boolean isSgxSupported;

    public SgxService(
            WorkerConfigurationService workerConfigService,
            DockerService dockerService,
            @Value("${debug.forceTeeDisabled}") boolean forceTeeDisabled
    ) {
        this.workerConfigService = workerConfigService;
        this.dockerService = dockerService;
        this.isSgxSupported = !forceTeeDisabled && isSgxSupported();
    }

    public boolean isSgxEnabled() {
        return isSgxSupported;
    }

    private boolean isSgxSupported() {
        log.info("Checking SGX support");
        boolean isSgxDriverFound = new File(SgxUtils.SGX_DRIVER_PATH).exists();
        if (!isSgxDriverFound) {
            LoggingUtils.printHighlightedMessage(
                    "SGX driver not found, worker will not run TEE tasks");
            return false;
        }
        if (!isSgxDevicePresent()) {
            String message = "SGX driver is installed but no SGX device was found " +
                             "(SGX not enabled?), worker will not run TEE tasks";
            LoggingUtils.printHighlightedMessage(message);
            return false;
        }
        log.info("SGX is enabled, worker can run TEE tasks");
        return true;
    }

    private boolean isSgxDevicePresent() {
        // "wallet-address-sgx-check" as containerName to avoid naming conflict
        // when running multiple workers on the same machine.
        String containerName = workerConfigService.getWorkerWalletAddress() + "-sgx-check";
        String alpineLatest = "alpine:latest";
        String cmd = "find /dev -name isgx -exec echo true ;";

        if (!dockerService.getClient().pullImage(alpineLatest)) {
            log.error("Failed to pull image for sgx check");
            return false;
        }

        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .imageUri(alpineLatest)
                .cmd(cmd)
                .maxExecutionTime(60000) // 1 min
                .binds(Collections.singletonList("/dev:/dev"))
                .build();


        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Failed to check SGX device, will continue without TEE support");
            return false;
        }

        String stdout = dockerRunResponse.getDockerLogs() != null ? dockerRunResponse.getDockerLogs().getStdout() : "";
        return stdout.replace("\n", "").equals("true");
    }
}