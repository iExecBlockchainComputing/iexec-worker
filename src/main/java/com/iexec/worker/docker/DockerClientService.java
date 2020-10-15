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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.iexec.common.utils.ArgsUtils;
import com.iexec.common.utils.WaitUtils;
import com.iexec.worker.sgx.SgxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
class DockerClientService {

    private static final String WORKER_DOCKER_NETWORK = "iexec-worker-net";
    private final DockerDaemonService dockerDaemonService;

    public DockerClientService(DockerDaemonService dockerDaemonService) {
        this.dockerDaemonService = dockerDaemonService;
    }

    // network

    String createNetwork(String networkName) {
        if (!getNetworkId(networkName).isEmpty()) {
            return "";
        }

        try (CreateNetworkCmd networkCmd =
                     getClient().createNetworkCmd()) {
            return networkCmd
                    .withName(networkName)
                    .withDriver("bridge")
                    .exec()
                    .getId();
        } catch (Exception e) {
            logError("create network", networkName, "", e);
        }
        return "";
    }

    String getNetworkId(String networkName) {
        try (ListNetworksCmd listNetworksCmd =
                     getClient().listNetworksCmd()) {
            List<Network> networks = listNetworksCmd
                    .withNameFilter(networkName)
                    .exec();
            for (Network network : networks) {
                if (network.getName().equals(networkName)) {
                    return network.getId();
                }
            }
        } catch (Exception e) {
            logError("get network id", networkName, "", e);
        }
        return "";
    }

    boolean removeNetwork(String networkId) {
        try (RemoveNetworkCmd removeNetworkCmd =
                     getClient().removeNetworkCmd(networkId)) {
            removeNetworkCmd.exec();
            return true;
        } catch (Exception e) {
            logError("remove network", "", networkId, e);
        }
        return false;
    }

    // image

    public boolean pullImage(String imageName) {
        NameParser.ReposTag repoAndTag =
                NameParser.parseRepositoryTag(imageName);
        if (repoAndTag.repos == null || repoAndTag.tag == null) {
            return false;
        }

        try (PullImageCmd pullImageCmd =
                     getClient().pullImageCmd(repoAndTag.repos)) {
            pullImageCmd
                    .withTag(repoAndTag.tag)
                    .exec(new PullImageResultCallback() {
                    })
                    .awaitCompletion(1, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            logError("pull image", imageName, "", e);
        }
        return false;
    }

    public String getImageId(String imageName) {
        try (ListImagesCmd listImagesCmd =
                     getClient().listImagesCmd()) {
            List<Image> images = listImagesCmd
                    .withDanglingFilter(false)
                    .withImageNameFilter(imageName)
                    .exec();
            for (Image image : images) {
                if (image == null || image.getRepoTags() == null) {
                    continue;
                }

                if (Arrays.asList(image.getRepoTags()).contains(imageName)) {
                    return image.getId();
                }
            }
        } catch (Exception e) {
            logError("get image id", imageName, "", e);
        }
        return "";
    }

    // container

    public String createContainer(DockerRunRequest dockerRunRequest) {
        if (dockerRunRequest == null) {
            return "";
        }

        String containerName = dockerRunRequest.getContainerName();
        if (containerName == null || containerName.isEmpty()) {
            return "";
        }

        String oldContainerId = getContainerId(containerName);

        if (!oldContainerId.isEmpty()) {
            logInfo("Container duplicate found", containerName,
                    oldContainerId);
            stopContainer(oldContainerId);
            removeContainer(oldContainerId);
        }

        if (getNetworkId(WORKER_DOCKER_NETWORK).isEmpty()
                && createNetwork(WORKER_DOCKER_NETWORK).isEmpty()) {
            //logInfo("Network created", WORKER_DOCKER_NETWORK, networkId);
            return "";
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(WORKER_DOCKER_NETWORK);

        if (dockerRunRequest.getBinds() != null && !dockerRunRequest.getBinds().isEmpty()) {
            hostConfig.withBinds(Binds.fromPrimitive(dockerRunRequest.getBinds().toArray(String[]::new)));
        }

        if (dockerRunRequest.isSgx()) {
            hostConfig
                    .withDevices(Device.parse(SgxService.SGX_DEVICE_PATH +
                            ":" + SgxService.SGX_DEVICE_PATH));
        }

        if (dockerRunRequest.getImageUri() == null || dockerRunRequest.getImageUri().isEmpty()) {
            return "";
        }

        try (CreateContainerCmd createContainerCmd = getClient()
                .createContainerCmd(dockerRunRequest.getImageUri())) {
            createContainerCmd
                    .withName(containerName)
                    .withHostConfig(hostConfig);

            if (dockerRunRequest.getCmd() != null && !dockerRunRequest.getCmd().isEmpty()) {
                createContainerCmd.withCmd(ArgsUtils.stringArgsToArrayArgs(dockerRunRequest.getCmd()));
            }
            if (dockerRunRequest.getEnv() != null && !dockerRunRequest.getEnv().isEmpty()) {
                createContainerCmd.withEnv(dockerRunRequest.getEnv());
            }
            if (dockerRunRequest.getContainerPort() > 0) {
                createContainerCmd.withExposedPorts(new ExposedPort(dockerRunRequest.getContainerPort()));
            }
            return createContainerCmd.exec().getId();
        } catch (Exception e) {
            logError("create container", containerName, "", e);
        }
        return "";
    }

    String getContainerId(String containerName) {
        try (ListContainersCmd listContainersCmd =
                     getClient().listContainersCmd()) {
            return listContainersCmd
                    .withShowAll(true)
                    .withNameFilter(Collections.singleton(containerName))
                    .exec()
                    .stream()
                    .findFirst()
                    .map(Container::getId)
                    .orElse("");
        } catch (Exception e) {
            logError("get container id", containerName, "", e);
        }
        return "";
    }

    public String getContainerStatus(String containerId) {
        if (containerId.isEmpty()) {
            return "";
        }

        try (InspectContainerCmd inspectContainerCmd =
                     getClient().inspectContainerCmd(containerId)) {
            return inspectContainerCmd.exec()
                    .getState()
                    .getStatus();
        } catch (Exception e) {
            logError("get container status",
                    getContainerName(containerId), containerId, e);
        }
        return "";
    }

    public boolean startContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }

        try (StartContainerCmd startContainerCmd =
                     getClient().startContainerCmd(containerId)) {
            startContainerCmd.exec();
            return true;
        } catch (Exception e) {
            logError("start container",
                    getContainerName(containerId), containerId, e);
        }
        return false;
    }

    public void waitContainerUntilExitOrTimeout(String containerId,
                                                Date executionTimeoutDate) {
        boolean isExited = false;
        boolean isTimeout = false;

        if (containerId.isEmpty()) {
            return;
        }

        int seconds = 0;
        String containerName = getContainerName(containerId);
        while (!isExited && !isTimeout) {
            if (seconds % 60 == 0) { //don't display logs too often
                log.info("Still running [containerName:{}, containerId:{}, " +
                                "status:{}, isExited:{}, isTimeout:{}]",
                        containerName, containerId,
                        getContainerStatus(containerId), isExited, isTimeout);
            }

            WaitUtils.sleep(1);
            isExited = getContainerStatus(containerId).equals("exited");
            isTimeout = new Date().after(executionTimeoutDate);
            seconds++;
        }

        if (isTimeout) {
            log.warn("Container reached timeout, stopping [containerId:{}, " +
                            "containerName:{}]",
                    containerName, containerId);
        }
    }

    public Optional<DockerLogs> getContainerLogs(String containerId) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (LogContainerCmd logContainerCmd =
                     getClient().logContainerCmd(containerId)) {
            logContainerCmd
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion();
        } catch (Exception e) {
            logError("get docker logs",
                    getContainerName(containerId), containerId, e);
            return Optional.empty();
        }
        return Optional.of(DockerLogs.builder()
                .stdout(stdout.toString())
                .stderr(stderr.toString())
                .build());
    }

    public boolean stopContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }

        List<String> statusesToStop = Arrays.asList("restarting", "running");
        if (!statusesToStop.contains(getContainerStatus(containerId))) {
            return true;
        }

        try (StopContainerCmd stopContainerCmd =
                     getClient().stopContainerCmd(containerId)) {
            stopContainerCmd.exec();
            return true;
        } catch (Exception e) {
            logError("stop container",
                    getContainerName(containerId), containerId, e);
        }
        return false;
    }

    public boolean removeContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }
        try (RemoveContainerCmd removeContainerCmd =
                     getClient().removeContainerCmd(containerId)) {
            removeContainerCmd.exec();
            return true;
        } catch (Exception e) {
            logError("remove container", "", containerId, e);
        }
        return false;
    }

    private String getContainerName(String containerId) {
        try (ListContainersCmd listContainersCmd =
                     getClient().listContainersCmd()) {
            return listContainersCmd
                    .withIdFilter(Collections.singleton(containerId))
                    .exec()
                    .stream()
                    .findFirst()
                    .map(Container::getNames)
                    .map(name -> name[0])
                    .orElse("");
        } catch (Exception e) {
            logError("get container name", "", containerId, e);
        }
        return "";
    }

    DockerClient getClient() {
        return dockerDaemonService.getClient();
    }

    private void logInfo(String infoMessage, String name, String id) {
        log.info("{} [name:'{}', id:'{}']", infoMessage, name, id);
    }

    private void logError(String failureContext, String name, String id,
                          Exception exception) {
        log.error("Failed to {} [name:'{}', id:'{}']",
                failureContext, name, id, exception);
    }

}
