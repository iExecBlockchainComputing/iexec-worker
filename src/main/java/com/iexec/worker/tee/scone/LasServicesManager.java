package com.iexec.worker.tee.scone;

import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeWorkflowConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class LasServicesManager {
    private final SconeConfiguration sconeConfiguration;
    private final SmsClientProvider smsClientProvider;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    // TODO: Purge entry when task is over (completed/failed)
    private final Map<String, LasService> chainTaskIdToLasService = new HashMap<>();
    private final Map<String, LasService> lasImageUriToLasService = new HashMap<>();

    public LasServicesManager(SconeConfiguration sconeConfiguration,
            SmsClientProvider smsClientProvider,
            WorkerConfigurationService workerConfigService,
            SgxService sgxService,
            DockerService dockerService) {
        this.sconeConfiguration = sconeConfiguration;
        this.smsClientProvider = smsClientProvider;
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

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(chainTaskId);

        final TeeWorkflowConfiguration config = smsClient.getTeeWorkflowConfiguration();
        if (config == null) {
            log.error("Missing tee workflow configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
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
        return "iexec-las-" + workerConfigService.getWorkerWalletAddress()
                + "-" + RandomStringUtils.random(10, true, true);
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
