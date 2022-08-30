package com.iexec.worker.tee;

import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeWorkflowConfiguration;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

    public TeeWorkflowConfiguration getOrCreateTeeWorkflowConfiguration(String chainTaskId) {
        return configurationForTask.computeIfAbsent(chainTaskId, this::buildTeeWorkflowConfiguration);
    }

    TeeWorkflowConfiguration buildTeeWorkflowConfiguration(String chainTaskId) {
        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(chainTaskId);

        final TeeWorkflowConfiguration config = smsClient.getTeeWorkflowConfiguration();
        log.info("Received tee workflow configuration [config:{}]", config);
        if (config == null) {
            throw new TeeWorkflowConfigurationCreationException(
                    "Missing tee workflow configuration [chainTaskId:" + chainTaskId +"]");
        }

        final String preComputeImage = config.getPreComputeImage();
        final String postComputeImage = config.getPostComputeImage();

        checkImageIsPresentOrDownload(preComputeImage, chainTaskId, "preComputeImage");
        checkImageIsPresentOrDownload(postComputeImage, chainTaskId, "postComputeImage");

        return TeeWorkflowConfiguration.builder()
                .preComputeImage(preComputeImage)
                .preComputeHeapSize(config.getPreComputeHeapSize())
                .preComputeEntrypoint(config.getPreComputeEntrypoint())
                .postComputeImage(postComputeImage)
                .postComputeHeapSize(config.getPostComputeHeapSize())
                .postComputeEntrypoint(config.getPostComputeEntrypoint())
                .build();
    }

    private void checkImageIsPresentOrDownload(String image, String chainTaskId, String imageType) {
        final DockerClientInstance client = dockerService.getClient(image);
        if (!client.isImagePresent(image)
                && !client.pullImage(image)) {
            throw new TeeWorkflowConfigurationCreationException(
                    "Failed to download image " +
                            "[chainTaskId:" + chainTaskId +", " + imageType + ":" + image + "]");
        }
    }
}
