package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeFramework;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Manages the {@link TeeServicesProperties}, providing an easy way to get properties for a task
 * and avoiding the need to create a new {@link TeeServicesProperties} instance each time.
 */
@Slf4j
@Service
public class TeeServicesPropertiesService implements Purgeable {
    private final SmsService smsService;
    private final DockerService dockerService;
    private final IexecHubAbstractService iexecHubService;

    private final Map<String, TeeServicesProperties> propertiesForTask = ExpiringTaskMapFactory.getExpiringTaskMap();

    public TeeServicesPropertiesService(SmsService smsService,
                                        DockerService dockerService,
                                        IexecHubAbstractService iexecHubService) {
        this.smsService = smsService;
        this.dockerService = dockerService;
        this.iexecHubService = iexecHubService;
    }

    public TeeServicesProperties getTeeServicesProperties(String chainTaskId) {
        return propertiesForTask.computeIfAbsent(chainTaskId, this::retrieveTeeServicesProperties);
    }

    <T extends TeeServicesProperties> T retrieveTeeServicesProperties(String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsService.getSmsClient(chainTaskId);
        final TeeFramework teeFramework = taskDescription.getTeeFramework();
        final TeeFramework smsTeeFramework = smsClient.getTeeFramework();
        if (smsTeeFramework != teeFramework) {
            throw new TeeServicesPropertiesCreationException(
                    "SMS is configured for another TEE framework" +
                            " [chainTaskId:" + chainTaskId +
                            ", requiredFramework:" + teeFramework +
                            ", actualFramework:" + smsTeeFramework + "]");
        }

        final T properties = smsClient.getTeeServicesProperties(teeFramework);
        log.info("Received TEE services properties [properties:{}]", properties);
        if (properties == null) {
            throw new TeeServicesPropertiesCreationException(
                    "Missing TEE services properties [chainTaskId:" + chainTaskId +"]");
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

    /**
     * Try and remove properties related to given task ID.
     * @param chainTaskId Task ID whose related properties should be purged
     * @return {@literal true} if key is not stored anymore,
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(String chainTaskId) {
        propertiesForTask.remove(chainTaskId);
        return !propertiesForTask.containsKey(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        propertiesForTask.clear();
    }
}
