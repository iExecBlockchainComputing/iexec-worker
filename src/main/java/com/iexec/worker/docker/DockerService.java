/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.containers.DockerRunFinalStatus;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.client.DockerClientFactory;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.iexec.commons.containers.client.DockerClientInstance.parseRegistryAddress;

@Slf4j
@Service
public class DockerService {

    private final Set<String> runningContainersRecord;
    private final WorkerConfigurationService workerConfigService;
    private final DockerRegistryConfiguration dockerRegistryConfiguration;
    private DockerClientInstance dockerClientInstance;

    public DockerService(WorkerConfigurationService workerConfigService,
                         DockerRegistryConfiguration dockerRegistryConfiguration) {
        this.dockerRegistryConfiguration = dockerRegistryConfiguration;
        this.runningContainersRecord = ConcurrentHashMap.newKeySet();
        this.workerConfigService = workerConfigService;
    }

    /**
     * Get an unauthenticated Docker client connected to the default docker registry
     * {@link DockerClientInstance#DEFAULT_DOCKER_REGISTRY}.
     *
     * @return an unauthenticated Docker client
     */
    public DockerClientInstance getClient() {
        if (dockerClientInstance == null) {
            dockerClientInstance = DockerClientFactory.getDockerClientInstance();
        }
        return dockerClientInstance;
    }

    /**
     * Try to get a Docker client that is authenticated to the registry of the provided image.
     * If no credentials are found for the identified registry, an unauthenticated Docker client
     * that is connected to the image's registry is provided instead.
     * <p>
     * e.g. for the image "registry.xyz/image:tag" we try to connect to
     * "registry.xyz" and for "iexechub/image:tag" we try to connect to docker.io.
     *
     * @param imageName
     * @return an authenticated Docker client if credentials for the image's registry are
     * to be found, an unauthenticated client otherwise.
     */
    public DockerClientInstance getClient(String imageName) {
        String registryAddress = parseRegistryAddress(imageName);
        Optional<RegistryCredentials> registryCredentials =
                dockerRegistryConfiguration.getRegistryCredentials(registryAddress);
        if (registryCredentials.isPresent()) {
            try {
                return getClient(
                        registryAddress,
                        registryCredentials.get().getUsername(),
                        registryCredentials.get().getPassword());
            } catch (Exception e) {
                log.error("Failed to get authenticated Docker client: [registry:{}, username:{}]",
                        registryAddress, registryCredentials.get().getUsername(), e);
            }
        }
        return DockerClientFactory.getDockerClientInstance(registryAddress);
    }

    /**
     * Get a Docker client that is authenticated to the specified registry.
     *
     * @param registryAddress  Registry address
     * @param registryUsername Username to authenticate on the registry
     * @param registryPassword Password to authenticate on the registry
     * @return An authenticated client
     */
    public DockerClientInstance getClient(String registryAddress,
                                          String registryUsername,
                                          String registryPassword) {
        if (StringUtils.isBlank(registryAddress) || StringUtils.isBlank(registryUsername)
                || StringUtils.isBlank(registryPassword)) {
            log.error("Registry parameters are required [registry:{}, username:{}]",
                    registryAddress, registryUsername);
            throw new IllegalArgumentException("All Docker registry parameters must be provided: "
                    + registryAddress);
        }
        return DockerClientFactory.getDockerClientInstance(
                registryAddress,
                registryUsername,
                registryPassword);
    }

    /**
     * All docker run requests initiated through this method will get their
     * yet-launched container kept in a local record.
     * <p>
     * If a container stops by itself (or receives a stop signal from the
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
                .finalStatus(DockerRunFinalStatus.FAILED)
                .build();
        String containerName = dockerRunRequest.getContainerName();
        if (!addToRunningContainersRecord(containerName)) {
            return dockerRunResponse;
        }
        dockerRunResponse = getClient().run(dockerRunRequest);
        if (dockerRunResponse.getFinalStatus() != DockerRunFinalStatus.SUCCESS
                || dockerRunRequest.getMaxExecutionTime() != 0) {
            removeFromRunningContainersRecord(containerName);
        }
        if (workerConfigService.isDeveloperLoggerEnabled()) {
            String chainTaskId = dockerRunRequest.getChainTaskId();
            if (StringUtils.isEmpty(chainTaskId)) {
                log.error("Cannot print developer logs [chainTaskId:{}]", chainTaskId);
            } else {
                log.info("Developer logs of docker run [chainTaskId:{}]{}", chainTaskId,
                        getComputeDeveloperLogs(chainTaskId, dockerRunResponse.getStdout(),
                                dockerRunResponse.getStderr()));
            }
        }
        return dockerRunResponse;
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
                IexecFileHelper.SLASH_IEXEC_IN;
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
                IexecFileHelper.SLASH_IEXEC_OUT;
    }

    /**
     * Stop all running containers launched by the worker via this current service
     * and remove them from running containers record. The container itself is not
     * removed here as it is removed by its watcher thread.
     */
    public void stopAllRunningContainers() {
        log.info("About to stop all running containers [runningContainers:{}]",
                runningContainersRecord);
        List.copyOf(runningContainersRecord)
                .forEach(this::stopRunningContainer);
    }

    /**
     * Stop running containers with names that contain the provided pattern and
     * remove them from the running containers record. This is typically used when
     * the worker aborts a task and needs to stop its pre-compute, compute, or
     * post-compute containers. The container itself is not removed here as it is
     * removed by its watcher thread.
     *
     * @param pattern containers whose name contains this pattern will be removed.
     * @return The remaining count of containers matching the provided predicate.
     */
    public long stopRunningContainersWithNameContaining(final String pattern) {
        log.info("Stopping containers with names containing the following pattern [pattern:{}]", pattern);
        final Predicate<String> containerNamePredicate = name -> name.contains(pattern);
        List.copyOf(runningContainersRecord).stream()
                .filter(containerNamePredicate)
                .forEach(this::stopRunningContainer);
        return List.copyOf(runningContainersRecord).stream()
                .filter(containerNamePredicate).count();
    }

    /**
     * Stop a running container with the provided name and remove it from the running containers record.
     * The container itself is not stopped here as it is removed by its watcher thread.
     *
     * @param containerName Name of container to stop
     */
    void stopRunningContainer(String containerName) {
        if (!getClient().isContainerPresent(containerName)) {
            log.error("No running container to be removed [containerName:{}]", containerName);
            return;
        }
        if (!getClient().isContainerActive(containerName)) {
            log.info("Container is not active it will be removed by its watcher thread"
                    + "[containerName:{}]", containerName);
        } else if (!getClient().stopContainer(containerName)) {
            log.error("Failed to stop running container [containerName:{}]", containerName);
            // Don't remove from record.
            return;
        }
        removeFromRunningContainersRecord(containerName);
    }

    /**
     * Get the record of running containers. Added originally for testing purposes.
     *
     * @return The current set of running containers
     */
    Set<String> getRunningContainersRecord() {
        return runningContainersRecord;
    }

    /**
     * Add a container to the running containers record
     *
     * @param containerName name of the container to be added to the record
     * @return true if container is added to the record, false otherwise
     */
    boolean addToRunningContainersRecord(String containerName) {
        if (StringUtils.isEmpty(containerName)) {
            log.error("Cannot add empty container name to record");
            return false;
        }
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

    private String getComputeDeveloperLogs(String chainTaskId, String stdout, String stderr) {
        File iexecIn = new File(workerConfigService.getTaskInputDir(chainTaskId));
        String iexecInTree = iexecIn.exists() ? FileHelper.printDirectoryTree(iexecIn) : "";
        iexecInTree = iexecInTree.replace("├── input/", "├── iexec_in/"); // confusing for developers if not replaced
        File iexecOut = new File(workerConfigService.getTaskIexecOutDir(chainTaskId));
        String iexecOutTree = iexecOut.exists() ? FileHelper.printDirectoryTree(iexecOut) : "";
        return LoggingUtils.prettifyDeveloperLogs(iexecInTree, iexecOutTree, stdout, stderr);
    }

}
