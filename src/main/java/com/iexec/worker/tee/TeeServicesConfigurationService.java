package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.common.utils.purge.Purgeable;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.docker.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the {@link TeeServicesProperties}, providing an easy way to get a configuration for a task
 * and avoiding the need to create a new {@link TeeServicesProperties} instance each time.
 */
@Slf4j
@Service
public class TeeServicesConfigurationService implements Purgeable {
    private final SmsClientProvider smsClientProvider;
    private final DockerService dockerService;
    private final IexecHubAbstractService iexecHubService;

    private final Map<String, TeeServicesProperties> propertiesForTask = new HashMap<>();

    public TeeServicesConfigurationService(SmsClientProvider smsClientProvider,
                                           DockerService dockerService,
                                           IexecHubAbstractService iexecHubService) {
        this.smsClientProvider = smsClientProvider;
        this.dockerService = dockerService;
        this.iexecHubService = iexecHubService;
    }

    public <T extends TeeServicesProperties> T getTeeServicesProperties(String chainTaskId) {
        //noinspection unchecked
        return (T) propertiesForTask.computeIfAbsent(chainTaskId, this::retrieveTeeServicesProperties);
    }

    <T extends TeeServicesProperties> T retrieveTeeServicesProperties(String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(taskDescription);
        final TeeEnclaveProvider teeEnclaveProvider = taskDescription.getTeeEnclaveProvider();
        final TeeEnclaveProvider smsTeeEnclaveProvider = smsClient.getTeeEnclaveProvider();
        if (smsTeeEnclaveProvider != teeEnclaveProvider) {
            throw new TeeServicesPropertiesCreationException(
                    "SMS is configured for another TEE enclave provider" +
                            " [chainTaskId:" + chainTaskId +
                            ", requiredProvider:" + teeEnclaveProvider +
                            ", actualProvider:" + smsTeeEnclaveProvider + "]");
        }

        final T properties = smsClient.getTeeServicesProperties(teeEnclaveProvider);
        log.info("Received TEE services configuration [properties:{}]", properties);
        if (properties == null) {
            throw new TeeServicesPropertiesCreationException(
                    "Missing TEE services configuration [chainTaskId:" + chainTaskId +"]");
        }

        final String preComputeImage = properties.getPreComputeProperties().getImage();
        final String postComputeImage = properties.getPostComputeProperties().getImage();

        checkImageIsPresentOrDownload(preComputeImage, chainTaskId, "preComputeImage");
        checkImageIsPresentOrDownload(postComputeImage, chainTaskId, "postComputeImage");

        return properties;
    }

    private void checkImageIsPresentOrDownload(String image, String chainTaskId, String imageType) {
        final DockerClientInstance client = dockerService.getClient(image);
        if (!client.isImagePresent(image)
                && !client.pullImage(image)) {
            throw new TeeServicesPropertiesCreationException(
                    "Failed to download image " +
                            "[chainTaskId:" + chainTaskId +", " + imageType + ":" + image + "]");
        }
    }

    @Override
    public boolean purgeTask(String chainTaskId) {
        return propertiesForTask.remove(chainTaskId) != null;
    }
}
