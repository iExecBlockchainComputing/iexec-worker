/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.docker;

import com.iexec.common.utils.FileHelper;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.*;


@Slf4j
@Service
public class DockerService {

    private final DockerClientService dockerClientService;
    private final WorkerConfigurationService workerConfigService;
    private final HashSet<String> runningContainersRecord;

    public DockerService(
            DockerClientService dockerClientService,
            WorkerConfigurationService workerConfigService) {
        this.dockerClientService = dockerClientService;
        this.workerConfigService = workerConfigService;
        this.runningContainersRecord = new HashSet<>();
    }

    public DockerRunResponse run(DockerRunRequest dockerRunRequest) {
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder().isSuccessful(false).build();
        String chainTaskId = dockerRunRequest.getChainTaskId();
        String containerName = dockerRunRequest.getContainerName();

        if (!addToRunningContainersRecord(containerName)){
            return dockerRunResponse;
        }

        String containerId = dockerClientService.createContainer(dockerRunRequest);
        if (containerId.isEmpty()) {
            return dockerRunResponse;
        }
        log.info("Created container [containerName:{}, containerId:{}]",
                containerName, containerId);

        if (!dockerClientService.startContainer(containerId)) {
            dockerClientService.removeContainer(containerId);
            return dockerRunResponse;
        }
        log.info("Started container [containerName:{}, containerId:{}]", containerName, containerId);

        if (dockerRunRequest.getMaxExecutionTime() == 0) {
            dockerRunResponse.setSuccessful(true);
            return dockerRunResponse;
        }

        Long exitCode = dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                Date.from(Instant.now().plusMillis(dockerRunRequest.getMaxExecutionTime())));
        removeFromRunningContainersRecord(containerName);
        boolean isTimeout = exitCode == null;

        if (isTimeout && !dockerClientService.stopContainer(containerId)) {
            return dockerRunResponse;
        }

        dockerClientService.getContainerLogs(containerId).ifPresent(containerLogs -> {
            dockerRunResponse.setDockerLogs(containerLogs);
            //TODO: Set exit code for improving internal and external developer experience
            if (shouldPrintDeveloperLogs(dockerRunRequest)) {
                log.info("Developer logs of computing stage [chainTaskId:{}, logs:{}]", chainTaskId,
                        getDockerExecutionDeveloperLogs(chainTaskId, containerLogs.getStdout()));
            }
        });

        if (!dockerClientService.removeContainer(containerId)) {
            return dockerRunResponse;
        }
        dockerRunResponse.setSuccessful(!isTimeout && exitCode == 0);
        return dockerRunResponse;
    }

    /**
     * Add a container to the running containers record
     *
     * @param containerName name of the container to be added to the record
     * @return true if container is added to the record
     */
    private boolean addToRunningContainersRecord(String containerName) {
        if (runningContainersRecord.contains(containerName)){
            log.error("Failed to add running container to record, container is " +
                    "already on the record [containerName:{}]", containerName);
            return false;
        }
        return runningContainersRecord.add(containerName);
    }

    /**
     * Remove a container from the running containers record
     *
     * @param containerName name of the container to be removed from the record
     */
    private void removeFromRunningContainersRecord(String containerName) {
        if (runningContainersRecord.contains(containerName)){
            log.error("Failed to remove running container from record, container " +
                    "does not exist [containerName:{}]", containerName);
            return;
        }
        runningContainersRecord.remove(containerName);
    }

    public boolean pullImage(String image) {
        return dockerClientService.pullImage(image);
    }

    public boolean isImagePulled(String image) {
        return !dockerClientService.getImageId(image).isEmpty();
    }

    public boolean stopAndRemoveContainer(String containerName) {
        if (dockerClientService.stopContainer(containerName)) {
            return dockerClientService.removeContainer(containerName);
        }
        return false;
    }

    boolean shouldPrintDeveloperLogs(DockerRunRequest dockerRunRequest) {
        return workerConfigService.isDeveloperLoggerEnabled() && dockerRunRequest.isShouldDisplayLogs();
    }

    private String getDockerExecutionDeveloperLogs(String chainTaskId, String stdout) {
        String iexecInTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskInputDir(chainTaskId)));
        iexecInTree = iexecInTree.replace("├── input/", "├── iexec_in/");//confusing for developers if not replaced
        String iexecOutTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskIexecOutDir(chainTaskId)));
        return LoggingUtils.prettifyDeveloperLogs(iexecInTree, iexecOutTree, stdout);
    }

    /**
     * This method will stop all running containers launched by the worker via
     * this current service.
     */
    public void stopRunningContainers() {
        log.info("About to stop all running containers [runningContainers:{}]",
                runningContainersRecord);
        runningContainersRecord.stream()
                .filter(containerName ->
                        !dockerClientService.stopContainer(containerName))
                .forEach(unstoppedContainer ->
                        log.error("Failed to stop one container among all running " +
                                "[unstoppedContainer:{}]", unstoppedContainer));
    }

}