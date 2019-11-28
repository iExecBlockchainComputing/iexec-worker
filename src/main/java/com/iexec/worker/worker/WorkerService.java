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

    private CredentialsService credentialsService;
    private WorkerConfigurationService workerConfigService;
    private CoreConfigurationService coreConfigService;
    private PublicConfigurationService publicConfigService;
    private CustomCoreFeignClient customCoreFeignClient;
    private VersionService versionService;
    private SconeTeeService sconeTeeService;
    private RestartEndpoint restartEndpoint;

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
        log.info("Number of max parallel tasks on this machine [tasks:{}]", workerConfigService.getNbCPU() / 2);
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