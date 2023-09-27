/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.utils.WaitUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.tee.scone.TeeSconeService;
import com.iexec.worker.utils.LoggingUtils;
import com.iexec.worker.version.VersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
public class WorkerService {

    private final CredentialsService credentialsService;
    private final WorkerConfigurationService workerConfigService;
    private final CoreConfigurationService coreConfigService;
    private final PublicConfigurationService publicConfigService;
    private final CustomCoreFeignClient customCoreFeignClient;
    private final VersionService versionService;
    private final TeeSconeService teeSconeService;
    private final RestartEndpoint restartEndpoint;
    private final DockerService dockerService;

    public WorkerService(
            CredentialsService credentialsService,
            WorkerConfigurationService workerConfigService,
            CoreConfigurationService coreConfigService,
            PublicConfigurationService publicConfigService,
            CustomCoreFeignClient customCoreFeignClient,
            VersionService versionService,
            TeeSconeService teeSconeService,
            RestartEndpoint restartEndpoint,
            DockerService dockerService) {
        this.credentialsService = credentialsService;
        this.workerConfigService = workerConfigService;
        this.coreConfigService = coreConfigService;
        this.publicConfigService = publicConfigService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.versionService = versionService;
        this.teeSconeService = teeSconeService;
        this.restartEndpoint = restartEndpoint;
        this.dockerService = dockerService;
    }

    public boolean registerWorker() {
        log.info("Number of CPUs [CPUs:{}]", workerConfigService.getCpuCount());
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
                .cpuNb(workerConfigService.getCpuCount())
                .memorySize(workerConfigService.getMemorySize())
                .teeEnabled(teeSconeService.isTeeEnabled())
                .gpuEnabled(workerConfigService.isGpuEnabled())
                .build();

        customCoreFeignClient.registerWorker(model);
        log.info("Registered the worker to the core [worker:{}]", model);
        return true;
    }

    /**
     * Before restarting, the worker will ask the core if the worker still
     * has computing task in progress.
     * If the worker has computing task in progress, it won't restart.
         The worker will retry to restart in the next pings once there is
         no more computing tasks.
     * If the worker hasn't computing task in progress, it will immediately restart
     * <p>
     * Note: In case of a restart, to avoid launched running containers to become
     * future orphans in the next worker session, running containers started by
     * the worker will all be stopped. Containers will be automatically removed
     * by already existing threads watching for container exit.
     */
    public void restartGracefully() {
        List<String> computingTasks = customCoreFeignClient.getComputingTasks();
        if (!computingTasks.isEmpty()) {
            log.warn("The worker will wait before restarting since computing " +
                    "tasks are in progress [computingTasks:{}]", computingTasks);
            return;
        }
        dockerService.stopAllRunningContainers();
        // give 1 second to threads watching stopped containers
        // to remove them.
        WaitUtils.sleep(1);
        log.warn("The worker is about to restart");
        restartEndpoint.restart();
    }

    /**
     * Fixes: required a bean of type
     * 'org.springframework.cloud.context.restart.RestartEndpoint'
     * could not be found.
     */
    @Bean
    public static RestartEndpoint restartEndpoint() {
        return new RestartEndpoint();
    }
}
