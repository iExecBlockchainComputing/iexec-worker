package com.iexec.worker.tee.scone;

import com.iexec.sms.api.config.SconeServicesConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class LasServicesManager {
    private final SconeConfiguration sconeConfiguration;
    private final TeeServicesConfigurationService teeServicesConfigurationService;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    private final Map<String, LasService> chainTaskIdToLasService = new HashMap<>();
    private final Map<String, LasService> lasImageUriToLasService = new HashMap<>();

    public LasServicesManager(SconeConfiguration sconeConfiguration,
            TeeServicesConfigurationService teeServicesConfigurationService,
            WorkerConfigurationService workerConfigService,
            SgxService sgxService,
            DockerService dockerService) {
        this.sconeConfiguration = sconeConfiguration;
        this.teeServicesConfigurationService = teeServicesConfigurationService;
        this.workerConfigService = workerConfigService;
        this.sgxService = sgxService;
        this.dockerService = dockerService;
    }

    public boolean startLasService(String chainTaskId) {
        // Just checking no LAS is already created/started for this task
        final LasService alreadyCreatedLas = getLas(chainTaskId);
        if (alreadyCreatedLas != null) {
            return alreadyCreatedLas.isStarted() || alreadyCreatedLas.start();
        }

        final SconeServicesConfiguration config = teeServicesConfigurationService.getTeeServicesConfiguration(chainTaskId);
        if (config == null) {
            log.error("Missing Scone services configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
            return false;
        }
        final String lasImageUri = !StringUtils.isEmpty(config.getLasImage())
                ? config.getLasImage()
                : "";

        final LasService lasService = lasImageUriToLasService.computeIfAbsent(
                lasImageUri,
                this::createLasService);
        chainTaskIdToLasService.putIfAbsent(chainTaskId, lasService);
        return lasService.isStarted() || lasService.start();
    }

    /**
     * Create LAS container name with a random salt to avoid naming conflicts
     * when running multiple workers on the same machine or using multiple SMS.
     * Note: Starting from 64 characters, `failed to lookup address
     * information: Name does not resolve` error occures.
     */
    String createLasContainerName() {
        return "iexec-las-" + workerConfigService.getWorkerWalletAddress() + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    LasService createLasService(String lasImageUri) {
        return new LasService(
                createLasContainerName(),
                lasImageUri,
                sconeConfiguration,
                workerConfigService,
                sgxService,
                dockerService);
    }

    @PreDestroy
    void stopLasServices() {
        lasImageUriToLasService.values()
                .forEach(LasService::stopAndRemoveContainer);
    }

    public LasService getLas(String chainTaskId) {
        return chainTaskIdToLasService.get(chainTaskId);
    }
}
