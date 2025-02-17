/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.tee;

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.chain.IexecHubAbstractService;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

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

    public TeeServicesProperties getTeeServicesProperties(final String chainTaskId) {
        return propertiesForTask.computeIfAbsent(chainTaskId, this::retrieveTeeServicesProperties);
    }

    <T extends TeeServicesProperties> T retrieveTeeServicesProperties(final String chainTaskId) {
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

        final TeeEnclaveConfiguration teeEnclaveConfiguration = taskDescription.getAppEnclaveConfiguration();
        Objects.requireNonNull(teeEnclaveConfiguration, "Missing TEE enclave configuration [chainTaskId:" + chainTaskId + "]");
        
        final T properties = smsClient.getTeeServicesPropertiesVersion(teeFramework, teeEnclaveConfiguration.getVersion());
        log.info("Received TEE services properties [properties:{}]", properties);
        if (properties == null) {
            throw new TeeServicesPropertiesCreationException(
                    "Missing TEE services properties [chainTaskId:" + chainTaskId + "]");
        }

        final String preComputeImage = properties.getPreComputeProperties().getImage();
        final String postComputeImage = properties.getPostComputeProperties().getImage();

        checkImageIsPresentOrDownload(preComputeImage, chainTaskId, "preComputeImage");
        checkImageIsPresentOrDownload(postComputeImage, chainTaskId, "postComputeImage");

        return properties;
    }

    private void checkImageIsPresentOrDownload(final String image, final String chainTaskId, final String imageType) {
        final DockerClientInstance client = dockerService.getClient(image);
        if (!client.isImagePresent(image)
                && !client.pullImage(image)) {
            throw new TeeServicesPropertiesCreationException(
                    "Failed to download image " +
                            "[chainTaskId:" + chainTaskId + ", " + imageType + ":" + image + "]");
        }
    }

    /**
     * Try and remove properties related to given task ID.
     *
     * @param chainTaskId Task ID whose related properties should be purged
     * @return {@literal true} if key is not stored anymore,
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        propertiesForTask.remove(chainTaskId);
        return !propertiesForTask.containsKey(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        propertiesForTask.clear();
    }
}
