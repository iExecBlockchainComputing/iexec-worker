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
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.Date;

@Service
public class DockerService {

    private final DockerClientInstance dockerClientInstance;
    private final WorkerConfigurationService workerConfigService;

    public DockerService(WorkerConfigurationService workerConfigService) {
        this.dockerClientInstance = DockerClientFactory.get();
        this.workerConfigService = workerConfigService;
    }

    public DockerClientInstance getClient() {
        return this.dockerClientInstance;
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

        Long exitCode = dockerClientService.waitContainerUntilExitOrTimeout(containerId,
                Date.from(Instant.now().plusMillis(dockerRunRequest.getMaxExecutionTime())));
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