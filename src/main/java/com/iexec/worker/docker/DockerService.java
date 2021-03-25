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

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientFactory;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
public class DockerService {

    private final DockerClientInstance dockerClientInstance;
    private final HashSet<String> runningContainersRecord;
    private final WorkerConfigurationService workerConfigService;

    public DockerService(WorkerConfigurationService workerConfigService) {
        this.dockerClientInstance = DockerClientFactory.getDockerClientInstance();
        this.runningContainersRecord = new HashSet<>();
        this.workerConfigService = workerConfigService;
    }

    public DockerClientInstance getClient() {
        return this.dockerClientInstance;
    }

    /**
     * All docker run requests initiated through this method will get their
     * yet-launched container kept in a local record.
     * <p>
     * If a container stops by itself (or receives a stop signal from this
     * outside), the container will be automatically docker removed (unless if
     * started with maxExecutionTime = 0) in addition to be removed from the local
     * record.
     * If the worker has to abort on a task or shutdown, it should remove all
     * running container created by itself to avoid container orphans.
     *
     * @param dockerRunRequest docker run request
     * @return docker run response
     */
    public DockerRunResponse run(DockerRunRequest dockerRunRequest) {
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder()
                .isSuccessful(false)
                .build();
        String containerName = dockerRunRequest.getContainerName();
        if (!addToRunningContainersRecord(containerName)) {
            return dockerRunResponse;
        }
        dockerRunResponse = getClient().run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()
            || dockerRunRequest.getMaxExecutionTime() != 0) {
            removeFromRunningContainersRecord(containerName);
        }
        if (shouldPrintDeveloperLogs(dockerRunRequest)) {
            String chainTaskId = dockerRunRequest.getChainTaskId();
            if (!StringUtils.hasText(chainTaskId)) {
                log.error("Cannot print developer logs [chainTaskId:{}]", chainTaskId);
            } else {
                log.info("Developer logs of compute stage [chainTaskId:{}]{}", chainTaskId,
                        getComputeDeveloperLogs(chainTaskId, dockerRunResponse.getStdout(),
                                dockerRunResponse.getStderr()));
            }
        }
        return dockerRunResponse;
    }

    /**
     * Add a container to the running containers record
     *
     * @param containerName name of the container to be added to the record
     * @return true if container is added to the record
     */
    boolean addToRunningContainersRecord(String containerName) {
        if (runningContainersRecord.contains(containerName)) {
            log.error("Failed to add running container to record, container is " +
                    "already on the record [containerName:{}]", containerName);
            return false;
        }
        return runningContainersRecord.add(containerName);
    }

    /**
     * Get docker volume bind shared between the host and
     * the container for input.
     * <p>
     * Expected: taskBaseDir/input:/iexec_in
     * 
     * @param chainTaskId
     * @return
     */
    public String getInputBind(String chainTaskId) {
        return workerConfigService.getTaskInputDir(chainTaskId) + ":" +
                FileHelper.SLASH_IEXEC_IN;
    }

    /**
     * Get docker volume bind shared between the host and
     * the container for output.
     * <p>
     * Expected: taskBaseDir/output/iexec_out:/iexec_out
     * 
     * @param chainTaskId
     * @return
     */
    public String getIexecOutBind(String chainTaskId) {
        return workerConfigService.getTaskIexecOutDir(chainTaskId) + ":" +
                FileHelper.SLASH_IEXEC_OUT;
    }

    /**
     * Remove a container from the running containers record
     *
     * @param containerName name of the container to be removed from the record
     * @return false if container to added to the record
     */
    boolean removeFromRunningContainersRecord(String containerName) {
        if (!runningContainersRecord.contains(containerName)) {
            log.error("Failed to remove running container from record, container " +
                    "does not exist [containerName:{}]", containerName);
            return false;
        }
        return runningContainersRecord.remove(containerName);
    }

    /**
     * This method will stop all running containers launched by the worker via
     * this current service.
     */
    public void stopRunningContainers() {
        log.info("About to stop all running containers [runningContainers:{}]",
                runningContainersRecord);
        List.copyOf(runningContainersRecord).forEach(containerName -> {
            if (!getClient().stopContainer(containerName)) {
                log.error("Failed to stop one container among all running " +
                        "[unstoppedContainer:{}]", containerName);
                return;
            }
            removeFromRunningContainersRecord(containerName);
        });
    }

    private boolean shouldPrintDeveloperLogs(DockerRunRequest dockerRunRequest) {
        return workerConfigService.isDeveloperLoggerEnabled() && dockerRunRequest.isShouldDisplayLogs();
    }

    private String getComputeDeveloperLogs(String chainTaskId, String stdout, String stderr) {
        String iexecInTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskInputDir(chainTaskId)));
        iexecInTree = iexecInTree.replace("├── input/", "├── iexec_in/"); // confusing for developers if not replaced
        String iexecOutTree = FileHelper.printDirectoryTree(new File(workerConfigService.getTaskIexecOutDir(chainTaskId)));
        return LoggingUtils.prettifyDeveloperLogs(iexecInTree, iexecOutTree, stdout, stderr);
    }

}