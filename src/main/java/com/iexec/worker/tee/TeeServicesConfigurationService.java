package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.config.TeeServicesConfiguration;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the {@link TeeServicesConfiguration}, providing an easy way to get a configuration for a task
 * and avoiding the need to create a new {@link TeeServicesConfiguration} instance each time.
 */
@Slf4j
@Service
public class TeeServicesConfigurationService {
    private final SmsClientProvider smsClientProvider;
    private final DockerService dockerService;
    private final IexecHubAbstractService iexecHubService;

    // TODO: purge this map when a task has been completed
    private final Map<String, TeeServicesConfiguration> configurationForTask = new HashMap<>();

    public TeeServicesConfigurationService(SmsClientProvider smsClientProvider,
                                           DockerService dockerService,
                                           IexecHubAbstractService iexecHubService) {
        this.smsClientProvider = smsClientProvider;
        this.dockerService = dockerService;
        this.iexecHubService = iexecHubService;
    }

    public <T extends TeeServicesConfiguration> T getTeeServicesConfiguration(String chainTaskId) {
        //noinspection unchecked
        return (T) configurationForTask.computeIfAbsent(chainTaskId, this::retrieveTeeServicesConfiguration);
    }

    <T extends TeeServicesConfiguration> T retrieveTeeServicesConfiguration(String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(taskDescription);
        final TeeEnclaveProvider teeEnclaveProvider = taskDescription.getTeeEnclaveProvider();

        final T config = smsClient.getTeeServicesConfiguration(teeEnclaveProvider);
        log.info("Received TEE services configuration [config:{}]", config);
        if (config == null) {
            throw new TeeServicesConfigurationCreationException(
                    "Missing TEE services configuration [chainTaskId:" + chainTaskId +"]");
        }

        final String preComputeImage = config.getPreComputeConfiguration().getImage();
        final String postComputeImage = config.getPostComputeConfiguration().getImage();

        checkImageIsPresentOrDownload(preComputeImage, chainTaskId, "preComputeImage");
        checkImageIsPresentOrDownload(postComputeImage, chainTaskId, "postComputeImage");

        return config;
    }

    private void checkImageIsPresentOrDownload(String image, String chainTaskId, String imageType) {
        final DockerClientInstance client = dockerService.getClient(image);
        if (!client.isImagePresent(image)
                && !client.pullImage(image)) {
            throw new TeeServicesConfigurationCreationException(
                    "Failed to download image " +
                            "[chainTaskId:" + chainTaskId +", " + imageType + ":" + image + "]");
        }
    }
}
