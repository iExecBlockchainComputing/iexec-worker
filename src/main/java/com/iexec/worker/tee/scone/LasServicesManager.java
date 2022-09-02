package com.iexec.worker.tee.scone;

import com.iexec.common.utils.purge.Purgeable;
import com.iexec.sms.api.config.SconeServicesConfiguration;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesConfigurationService;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LasServicesManager implements Purgeable {
    private final SconeConfiguration sconeConfiguration;
    private final TeeServicesConfigurationService teeServicesConfigurationService;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    private final Map<String, LasService> chainTaskIdToLasService = ExpiringMap
            .builder()
            .expiration(100, TimeUnit.HOURS)
            .build();
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
     * "iexec-las-0xWalletAddress-timestamp" as lasContainerName to avoid naming conflict
     * when running multiple workers on the same machine or using multiple SMS.
     */
    String createLasContainerName() {
        return "iexec-las-" + workerConfigService.getWorkerWalletAddress() + "-" + new Date().getTime();
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
        lasImageUriToLasService.values().forEach(LasService::stopAndRemoveContainer);
    }

    public LasService getLas(String chainTaskId) {
        return chainTaskIdToLasService.get(chainTaskId);
    }

    @Override
    public boolean purgeTask(String chainTaskId) {
        return chainTaskIdToLasService.remove(chainTaskId) != null;
    }
}
