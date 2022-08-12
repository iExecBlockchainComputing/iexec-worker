package com.iexec.worker.tee.scone;

import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import com.iexec.sms.api.SmsClientProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class LasServicesManager {
    private final SconeConfiguration sconeConfiguration;
    private final SmsClientProvider smsClientProvider;
    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    private final Map<String, LasService> lasForTask = new HashMap<>();
    private final Map<String, LasService> startedLasServices = new HashMap<>();

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
        final Optional<SmsClient> oSmsClient = smsClientProvider.getSmsClientForTask(chainTaskId);
        if (oSmsClient.isEmpty()) {
            throw new RuntimeException("No SMS set for task [chainTaskId:" + chainTaskId +"]");
        }

        final SmsClient smsClient = oSmsClient.get();

        final TeeWorkflowSharedConfiguration config = smsClient.getTeeWorkflowConfiguration();
        if (config == null) {
            throw new RuntimeException("Missing tee workflow configuration");
        }
        final String lasImageUri = !StringUtils.isEmpty(config.getLasImage())
                ? config.getLasImage()
                : "";

        // "iexec-las-0xWalletAddress-timestamp" as lasContainerName to avoid naming conflict
        // when running multiple workers on the same machine or using multiple SMS.
        final String lasContainerName = createLasContainerName();

        final LasService lasService = startedLasServices.computeIfAbsent(
                lasContainerName,
                containerName -> createLasService(lasImageUri, containerName));
        lasForTask.putIfAbsent(chainTaskId, lasService);
        return lasService.isStarted() || lasService.start();
    }

    String createLasContainerName() {
        return "iexec-las-" + workerConfigService.getWorkerWalletAddress() + "-" + new Date().getTime();
    }

    LasService createLasService(String lasImageUri, String containerName) {
        return new LasService(
                containerName,
                lasImageUri,
                sconeConfiguration,
                workerConfigService,
                sgxService,
                dockerService);
    }

    @PreDestroy
    void stopLasService() {
        startedLasServices.values().forEach(LasService::stop);
    }

    public LasService getLas(String chainTaskId) {
        return lasForTask.get(chainTaskId);
    }
}