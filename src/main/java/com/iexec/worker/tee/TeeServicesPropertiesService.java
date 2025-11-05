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
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.workflow.WorkflowError;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;
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
    private final IexecHubService iexecHubService;
    private final WorkerConfigurationService workerConfigurationService;

    private final Map<String, TeeServicesProperties> propertiesForTask = ExpiringTaskMapFactory.getExpiringTaskMap();

    public TeeServicesPropertiesService(final SmsService smsService,
                                        final DockerService dockerService,
                                        final IexecHubService iexecHubService,
                                        final WorkerConfigurationService workerConfigurationService) {
        this.smsService = smsService;
        this.dockerService = dockerService;
        this.iexecHubService = iexecHubService;
        this.workerConfigurationService = workerConfigurationService;
    }

    public TeeServicesProperties getTeeServicesProperties(final String chainTaskId) {
        return propertiesForTask.get(chainTaskId);
    }

    public List<WorkflowError> retrieveTeeServicesProperties(final String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        // FIXME better errors
        final TeeEnclaveConfiguration teeEnclaveConfiguration = taskDescription.getAppEnclaveConfiguration();
        if (teeEnclaveConfiguration == null) {
            log.error("No enclave configuration found for task [chainTaskId:{}]", chainTaskId);
            return List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_MISSING_ENCLAVE_CONFIGURATION));
        }
        if (!teeEnclaveConfiguration.getValidator().isValid()) {
            log.error("Invalid enclave configuration [chainTaskId:{}, violations:{}]",
                    chainTaskId, teeEnclaveConfiguration.getValidator().validate().toString());
            return List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INVALID_ENCLAVE_CONFIGURATION));
        }
        long teeComputeMaxHeapSize = DataSize
                .ofGigabytes(workerConfigurationService.getTeeComputeMaxHeapSizeGb())
                .toBytes();
        if (teeEnclaveConfiguration.getHeapSize() > teeComputeMaxHeapSize) {
            log.error("Enclave configuration should define a proper heap size [chainTaskId:{}, heapSize:{}, maxHeapSize:{}]",
                    chainTaskId, teeEnclaveConfiguration.getHeapSize(), teeComputeMaxHeapSize);
            return List.of(new WorkflowError(ReplicateStatusCause.PRE_COMPUTE_INVALID_ENCLAVE_HEAP_CONFIGURATION));
        }

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsService.getSmsClient(chainTaskId);
        final TeeFramework teeFramework = taskDescription.getTeeFramework();
        final TeeFramework smsTeeFramework = smsClient.getTeeFramework();
        if (smsTeeFramework != teeFramework) {
            return List.of(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                    String.format("SMS is configured for another TEE framework [chainTaskId:%s, requiredFramework:%s, actualFramework:%s]",
                            chainTaskId, teeFramework, smsTeeFramework)));
        }

        final TeeServicesProperties properties = smsClient.getTeeServicesPropertiesVersion(teeFramework, teeEnclaveConfiguration.getVersion());
        log.info("Received TEE services properties [properties:{}]", properties);
        if (properties == null) {
            return List.of(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                    String.format("Missing TEE services properties [chainTaskId:%s]", chainTaskId)));
        }

        final String preComputeImage = properties.getPreComputeProperties().getImage();
        final String postComputeImage = properties.getPostComputeProperties().getImage();
        final List<WorkflowError> errors = new ArrayList<>();

        errors.addAll(checkImageIsPresentOrDownload(preComputeImage, chainTaskId, "preComputeImage"));
        errors.addAll(checkImageIsPresentOrDownload(postComputeImage, chainTaskId, "postComputeImage"));

        if (errors.isEmpty()) {
            propertiesForTask.put(chainTaskId, properties);
        }
        return List.copyOf(errors);
    }

    private List<WorkflowError> checkImageIsPresentOrDownload(final String image, final String chainTaskId, final String imageType) {
        final DockerClientInstance client = dockerService.getClient(image);
        if (!client.isImagePresent(image) && !client.pullImage(image)) {
            return List.of(new WorkflowError(ReplicateStatusCause.GET_TEE_SERVICES_CONFIGURATION_FAILED,
                    String.format("Failed to download image [chainTaskId:%s, %s:%s]", chainTaskId, imageType, image)));
        }
        return List.of();
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
