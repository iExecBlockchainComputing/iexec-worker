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

package com.iexec.worker.worker;

import com.iexec.common.config.WorkerModel;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.LoggingUtils;
import com.iexec.worker.utils.version.VersionService;

import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class WorkerService {

    private final CredentialsService credentialsService;
    private final WorkerConfigurationService workerConfigService;
    private final CoreConfigurationService coreConfigService;
    private final PublicConfigurationService publicConfigService;
    private final CustomCoreFeignClient customCoreFeignClient;
    private final VersionService versionService;
    private final SconeTeeService sconeTeeService;
    private final RestartEndpoint restartEndpoint;

    public WorkerService(CredentialsService credentialsService,
                         WorkerConfigurationService workerConfigService,
                         CoreConfigurationService coreConfigService,
                         PublicConfigurationService publicConfigService,
                         CustomCoreFeignClient customCoreFeignClient,
                         VersionService versionService,
                         SconeTeeService sconeTeeService,
                         RestartEndpoint restartEndpoint) {
        this.credentialsService = credentialsService;
        this.workerConfigService = workerConfigService;
        this.coreConfigService = coreConfigService;
        this.publicConfigService = publicConfigService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.versionService = versionService;
        this.sconeTeeService = sconeTeeService;
        this.restartEndpoint = restartEndpoint;
    }

    public boolean registerWorker() {
        log.info("Number of CPUs [CPUs:{}]", workerConfigService.getNbCPU());
        log.info("Core URL [url:{}]", coreConfigService.getUrl());
        log.info("Core version [version:{}]", customCoreFeignClient.getCoreVersion());
        log.info("Getting public configuration from the core...");
        log.info("Got public configuration from the core [config:{}]", publicConfigService.getPublicConfiguration());

        if (!publicConfigService.getRequiredWorkerVersion().isEmpty() &&
                !versionService.getVersion().equals(publicConfigService.getRequiredWorkerVersion())) {

            String badVersion = String.format("Bad version! please upgrade your iexec-worker [current:%s, required:%s]",
                    versionService.getVersion(), publicConfigService.getRequiredWorkerVersion());

            LoggingUtils.printHighlightedMessage(badVersion);
            return false;
        }

        if (workerConfigService.getHttpProxyHost() != null && workerConfigService.getHttpProxyPort() != null) {
            log.info("Running with proxy [proxyHost:{}, proxyPort:{}]", workerConfigService.getHttpProxyHost(), workerConfigService.getHttpProxyPort());
        }

        String workerAddress = credentialsService.getCredentials().getAddress();

        WorkerModel model = WorkerModel.builder()
                .name(workerConfigService.getWorkerName())
                .walletAddress(workerAddress)
                .os(workerConfigService.getOS())
                .cpu(workerConfigService.getCPU())
                .cpuNb(workerConfigService.getNbCPU())
                .memorySize(workerConfigService.getMemorySize())
                .teeEnabled(sconeTeeService.isTeeEnabled())
                .gpuEnabled(workerConfigService.isGpuEnabled())
                .build();

        customCoreFeignClient.registerWorker(model);
        log.info("Registered the worker to the core [worker:{}]", model);
        return true;
    }

    public void restartApp() {
        restartEndpoint.restart();
    }
}