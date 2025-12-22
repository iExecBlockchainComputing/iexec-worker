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

package com.iexec.worker.worker;

import com.iexec.commons.poco.utils.WaitUtils;
import com.iexec.core.config.WorkerModel;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.SchedulerConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.tee.scone.TeeSconeService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WorkerService {

    private final String workerWalletAddress;
    private final WorkerConfigurationService workerConfigService;
    private final SchedulerConfiguration schedulerConfiguration;
    private final PublicConfigurationService publicConfigService;
    private final CustomCoreFeignClient customCoreFeignClient;
    private final BuildProperties buildProperties;
    private final TeeSconeService teeSconeService;
    private final RestartEndpoint restartEndpoint;
    private final DockerService dockerService;

    public WorkerService(
            WorkerConfigurationService workerConfigService,
            SchedulerConfiguration schedulerConfiguration,
            PublicConfigurationService publicConfigService,
            CustomCoreFeignClient customCoreFeignClient,
            BuildProperties buildProperties,
            TeeSconeService teeSconeService,
            RestartEndpoint restartEndpoint,
            DockerService dockerService,
            String workerWalletAddress) {
        this.workerConfigService = workerConfigService;
        this.schedulerConfiguration = schedulerConfiguration;
        this.publicConfigService = publicConfigService;
        this.customCoreFeignClient = customCoreFeignClient;
        this.buildProperties = buildProperties;
        this.teeSconeService = teeSconeService;
        this.restartEndpoint = restartEndpoint;
        this.dockerService = dockerService;
        this.workerWalletAddress = workerWalletAddress;
    }

    public boolean registerWorker() {
        log.info("Number of CPUs [CPUs:{}]", workerConfigService.getCpuCount());
        log.info("Core URL [url:{}]", schedulerConfiguration.getUrl());
        log.info("Core version [version:{}]", customCoreFeignClient.getCoreVersion());
        log.info("Getting public configuration from the core...");
        log.info("Got public configuration from the core [config:{}]", publicConfigService.getPublicConfiguration());

        if (!publicConfigService.getRequiredWorkerVersion().isEmpty() &&
                !buildProperties.getVersion().equals(publicConfigService.getRequiredWorkerVersion())) {

            String badVersion = String.format("Bad version! please upgrade your iexec-worker [current:%s, required:%s]",
                    buildProperties.getVersion(), publicConfigService.getRequiredWorkerVersion());

            LoggingUtils.printHighlightedMessage(badVersion);
            return false;
        }

        if (workerConfigService.getHttpProxyHost() != null && workerConfigService.getHttpProxyPort() != null) {
            log.info("Running with proxy [proxyHost:{}, proxyPort:{}]", workerConfigService.getHttpProxyHost(), workerConfigService.getHttpProxyPort());
        }

        // FIXME add service to check TDX compatibility, use SgxService instead of TeeSconeService
        final WorkerModel model = WorkerModel.builder()
                .name(workerConfigService.getWorkerName())
                .walletAddress(workerWalletAddress)
                .os(workerConfigService.getOS())
                .cpu(workerConfigService.getCPU())
                .cpuNb(workerConfigService.getCpuCount())
                .memorySize(workerConfigService.getMemorySize())
                .gpuEnabled(workerConfigService.isGpuEnabled())
                .teeEnabled(teeSconeService.isTeeEnabled())
                .tdxEnabled(true)
                .build();

        customCoreFeignClient.registerWorker(model);
        log.info("Registered the worker to the core [worker:{}]", model);
        return true;
    }

    /**
     * Before restarting, the worker will ask the core if the worker still
     * has computing task in progress.
     * If the worker has computing task in progress, it won't restart.
     * The worker will retry to restart in the next pings once there is
     * no more computing tasks.
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
