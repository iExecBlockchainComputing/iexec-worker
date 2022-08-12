package com.iexec.worker.tee;

import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the {@link TeeWorkflowConfiguration}, providing an easy way to get a configuration for a task
 * and avoiding the need to create a new {@link TeeWorkflowConfiguration} instance each time.
 */
@Slf4j
@Service
public class TeeWorkflowConfigurationService {
    private final SmsClientProvider smsClientProvider;
    private final DockerService dockerService;

    // TODO: purge this map when a task has been completed
    private final Map<String, TeeWorkflowConfiguration> configurationForTask = new HashMap<>();

    public TeeWorkflowConfigurationService(SmsClientProvider smsClientProvider,
                                           DockerService dockerService) {
        this.smsClientProvider = smsClientProvider;
        this.dockerService = dockerService;
    }

    public TeeWorkflowConfiguration getTeeWorkflowConfiguration(String chainTaskId) {
        return configurationForTask.computeIfAbsent(chainTaskId, this::buildTeeWorkflowConfiguration);
    }

    TeeWorkflowConfiguration buildTeeWorkflowConfiguration(String chainTaskId) {
        final Optional<SmsClient> oSmsClient = smsClientProvider.getSmsClientForTask(chainTaskId);
        if (oSmsClient.isEmpty()) {
            throw new RuntimeException("No SMS set for task [chainTaskId:" + chainTaskId +"]");
        }

        final SmsClient smsClient = oSmsClient.get();
        final TeeWorkflowSharedConfiguration config = smsClient.getTeeWorkflowConfiguration();
        log.info("Received tee workflow configuration [config:{}]", config);
        if (config == null) {
            throw new RuntimeException("Missing tee workflow configuration [chainTaskId:" + chainTaskId +"]");
        }

        final String preComputeImage = config.getPreComputeImage();
        final String postComputeImage = config.getPostComputeImage();

        if (!dockerService.getClient(preComputeImage)
                .pullImage(preComputeImage)) {
            throw new RuntimeException("Failed to download pre-compute image " +
                    "[chainTaskId:" + chainTaskId +", preComputeImage:" + preComputeImage + "]");
        }
        if (!dockerService.getClient(postComputeImage)
                .pullImage(postComputeImage)) {
            throw new RuntimeException("Failed to download post-compute image " +
                    "[chainTaskId:" + chainTaskId +", postComputeImage:" + postComputeImage + "]");
        }
        return TeeWorkflowConfiguration.builder()
                .preComputeImage(preComputeImage)
                .preComputeHeapSize(config.getPreComputeHeapSize())
                .preComputeEntrypoint(config.getPreComputeEntrypoint())
                .postComputeImage(postComputeImage)
                .postComputeHeapSize(config.getPostComputeHeapSize())
                .postComputeEntrypoint(config.getPostComputeEntrypoint())
                .build();
    }
}