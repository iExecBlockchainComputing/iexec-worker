package com.iexec.worker.tee.scone;

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.sms.api.config.SconeServicesProperties;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class LasServicesManager implements Purgeable {
    private final SconeConfiguration sconeConfiguration;
    private final TeeServicesPropertiesService teeServicesPropertiesService;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    /**
     * Memoize Task-LAS container association.
     * As a LAS can be used by multiple tasks, no LAS can be stopped when a task is completed.
     */
    private final Map<String, LasService> chainTaskIdToLasService = ExpiringTaskMapFactory.getExpiringTaskMap();
    /**
     * Memoize LAS image-LAS container association.
     * This avoids starting multiple instances of the same LAS when used by different tasks.
     */
    private final Map<String, LasService> lasImageUriToLasService = new HashMap<>();

    public LasServicesManager(
            SconeConfiguration sconeConfiguration,
            TeeServicesPropertiesService teeServicesPropertiesService,
            WorkerConfigurationService workerConfigService,
            SgxService sgxService,
            DockerService dockerService) {
        this.sconeConfiguration = sconeConfiguration;
        this.teeServicesPropertiesService = teeServicesPropertiesService;
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

        final SconeServicesProperties properties = (SconeServicesProperties) teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);
        if (properties == null) {
            log.error("Missing Scone services configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
            return false;
        }
        if (StringUtils.isEmpty(properties.getLasImage())) {
            log.error("Missing Scone LAS OCI image name in Scone services configuration, can't start LAS [chainTaskId: {}]", chainTaskId);
            return false;
        }
        final String lasImageUri = properties.getLasImage();

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

    /**
     * Try and remove LAS service related to given task ID.
     * @param chainTaskId Task ID whose related LAS service should be purged
     * @return {@literal true} if key is not stored anymore,
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(String chainTaskId) {
        chainTaskIdToLasService.remove(chainTaskId);
        return !chainTaskIdToLasService.containsKey(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        chainTaskIdToLasService.clear();
    }
}
