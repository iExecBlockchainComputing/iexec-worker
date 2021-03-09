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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
public class DockerService {

    private final DockerClientInstance dockerClientInstance;
    private final HashSet<String> runningContainersRecord;

    public DockerService() {
        this.dockerClientInstance = DockerClientFactory.getDockerClientInstance();
        this.runningContainersRecord = new HashSet<>();
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

}