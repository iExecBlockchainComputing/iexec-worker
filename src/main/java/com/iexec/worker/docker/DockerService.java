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
import java.util.Date;


@Slf4j
@Service
public class DockerService {

    private final DockerClientService dockerClientService;
    private final WorkerConfigurationService workerConfigService;

    public DockerService(
            DockerClientService dockerClientService,
            WorkerConfigurationService workerConfigService
    ) {
        this.dockerClientService = dockerClientService;
        this.workerConfigService = workerConfigService;
    }

    public DockerRunResponse run(DockerRunRequest dockerRunRequest) {
        DockerRunResponse dockerRunResponse = DockerRunResponse.builder().isSuccessful(false).build();
        String chainTaskId = dockerRunRequest.getChainTaskId();

        String containerId = dockerClientService.createContainer(dockerRunRequest);
        if (containerId.isEmpty()) {
            return dockerRunResponse;
        }
        log.info("Created container [containerName:{}, containerId:{}]", dockerRunRequest.getContainerName(), containerId);

        if (!dockerClientService.startContainer(containerId)) {
            dockerClientService.removeContainer(containerId);
            return dockerRunResponse;
        }
        log.info("Started container [containerName:{}, containerId:{}]", dockerRunRequest.getContainerName(), containerId);

        if (dockerRunRequest.getMaxExecutionTime() == 0) {
            dockerRunResponse.setSuccessful(true);
            return dockerRunResponse;
        }

        dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                Date.from(Instant.now().plusMillis(dockerRunRequest.getMaxExecutionTime())));

        if (!dockerClientService.stopContainer(containerId)) {
            return dockerRunResponse;
        }

        dockerClientService.getContainerLogs(containerId).ifPresent(containerLogs -> {
            dockerRunResponse.setDockerLogs(containerLogs);
            if (shouldPrintDeveloperLogs(dockerRunRequest)) {
                log.info("Developer logs of computing stage [chainTaskId:{}, logs:{}]", chainTaskId,
                        getDockerExecutionDeveloperLogs(chainTaskId, containerLogs.getStdout()));
            }
        });

        if (!dockerClientService.removeContainer(containerId)) {
            return dockerRunResponse;
        }
        dockerRunResponse.setSuccessful(true);

        return dockerRunResponse;
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


}