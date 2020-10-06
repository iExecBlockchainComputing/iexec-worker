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
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
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

    public DockerClientService() {
        if (!isExistingNetwork(WORKER_DOCKER_NETWORK)) {
            createNetwork(WORKER_DOCKER_NETWORK);
        }
    }

    private static DockerClient getDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerTlsVerify(false).build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private void createNetwork(String networkName) {
        try {
            String networkId = getDockerClient().createNetworkCmd()
                    .withName(networkName)
                    .withDriver("bridge")
                    .exec().getId();
            log.info("Created network [networkName:{}, networkId:{}]", networkName, networkId);
        } catch (Exception e) {
            log.error("Failed to create network [networkName:{}, exception:{}]", networkName, e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isExistingNetwork(String networkName) {
        return !getNetworkId(networkName).isEmpty();
    }

    private String getNetworkId(String networkName) {
        try {
            for (Network network : getDockerClient().listNetworksCmd().withNameFilter(networkName).exec()) {
                if (network.getName().equals(networkName)) {
                    return network.getId();
                }
            }
        } catch (Exception e) {
            log.error("Failed to check if network is created [networkName:{}, exception:{}]", networkName, e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    public boolean pullImage(String imageName) {
        NameParser.ReposTag repoAndTag = NameParser.parseRepositoryTag(imageName);
        if (repoAndTag.repos == null || repoAndTag.tag == null) {
            log.error("Failed to pull image (imageName parsing) [imageName:{}]", imageName);
            return false;
        }

        try {
            getDockerClient().pullImageCmd(repoAndTag.repos)
                    .withTag(repoAndTag.tag)
                    .exec(new PullImageResultCallback() {
                    })
                    .awaitCompletion(1, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            log.error("Failed to pull image (imageName parsing) [imageName:{}]", imageName);
            e.printStackTrace();
        }
        return false;
    }

    public boolean isImagePulled(String imageName) {
        try {
            List<Image> images = getDockerClient().listImagesCmd().withDanglingFilter(false)
                    .withImageNameFilter(imageName).exec();
            for (Image image : images) {
                if (image == null || image.getRepoTags() == null) {
                    continue;
                }

                if (Arrays.asList(image.getRepoTags()).contains(imageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to check image pull[imageName:{}]", imageName);
            e.printStackTrace();
        }
        return false;
    }

    public String createContainer(DockerRunRequest dockerRunRequest, String containerName) {
        if (containerName == null || containerName.isEmpty()) {
            return "";
        }

        String oldContainerId = getContainerId(containerName);

        if (!oldContainerId.isEmpty()) {
            log.info("Found duplicate container with the same name, will remove it [containerName:{}, containerId:{}]",
                    containerName, oldContainerId);
            stopContainer(oldContainerId);
            removeContainer(oldContainerId);
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(WORKER_DOCKER_NETWORK);
        //.withCpuCount(1L)
        //.withMemory(1000000000L);

        if (dockerRunRequest.getBinds() != null && !dockerRunRequest.getBinds().isEmpty()) {
            hostConfig.withBinds(Binds.fromPrimitive(dockerRunRequest.getBinds().toArray(String[]::new)));
        }

        if (dockerRunRequest.isSgx()) {
            hostConfig
                    //.withDeviceCgroupRules(Arrays.asList("r", "w", "m")) //SgxService.SGX_CGROUP_PERMISSIONS) <--- why do we need this?
                    .withDevices(Device.parse(SgxService.SGX_DEVICE_PATH + ":" + SgxService.SGX_DEVICE_PATH));
        }

        if (dockerRunRequest.getImageUri() == null || dockerRunRequest.getImageUri().isEmpty()) {
            return "";
        }

        CreateContainerCmd createContainerCmd = getDockerClient().createContainerCmd(dockerRunRequest.getImageUri())
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

        try {
            return createContainerCmd.exec().getId();
        } catch (Exception e) {
            log.error("Failed to create container [containerName:{}, exception:{}]",
                    containerName, e.toString());
            e.printStackTrace();
        }
        return "";
    }

    public boolean startContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }
        try {
            getDockerClient().startContainerCmd(containerId).exec();
            return true;
        } catch (Exception e) {
            log.error("Failed to start container [containerId:{}, exception:{}]",
                    containerId, e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void waitContainerUntilExitOrTimeout(String containerId, Date executionTimeoutDate) {
        boolean isExited = false;
        boolean isTimeout = false;

        if (containerId.isEmpty()) {
            return;
        }

        int seconds = 0;
        while (!isExited && !isTimeout) {
            if (seconds % 60 == 0) { //don't display logs too often
                log.info("Still running [containerId:{}, status:{}, isExited:{}, isTimeout:{}]",
                        containerId, getContainerStatus(containerId), isExited, isTimeout);
            }

            WaitUtils.sleep(1);
            isExited = getContainerStatus(containerId).equals("exited");
            isTimeout = new Date().after(executionTimeoutDate);
            seconds++;
        }

        if (isTimeout) {
            log.warn("Container reached timeout, stopping [containerId:{}]",
                    containerId);
        }
    }

    public boolean stopContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }

        if (!Arrays.asList("restarting", "running").contains(getContainerStatus(containerId))) {
            return true;
        }

        try {
            getDockerClient().stopContainerCmd(containerId).exec();
            return true;
        } catch (Exception e) {
            log.error("Failed to stop container [containerId:{}, exception:{}]",
                    containerId, e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean removeContainer(String containerId) {
        if (containerId.isEmpty()) {
            return false;
        }
        try {
            getDockerClient().removeContainerCmd(containerId).exec();
            return true;
        } catch (Exception e) {
            log.error("Failed to remove container [containerId:{}, exception:{}]",
                    containerId, e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public Optional<DockerContainerLogs> getContainerLogs(String containerId) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try {
            getDockerClient()
                    .logContainerCmd(containerId).withStdOut(true).withStdErr(true)
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion();
        } catch (Exception e) {
            log.error("Failed to get docker logs [containerId:{}, e:{}, stderr:{}]", containerId, e, stderr.toString());
            return Optional.empty();
        }
        return Optional.of(DockerContainerLogs.builder().stdout(stdout.toString()).stderr(stderr.toString()).build());
    }

    public String getContainerStatus(String containerId) {
        if (containerId.isEmpty()) {
            return "";
        }
        try {
            return getDockerClient().inspectContainerCmd(containerId).exec().getState().getStatus();
        } catch (Exception e) {
            log.error("Failed to get container status [containerId:{}, exception:{}]",
                    containerId, e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    private String getContainerId(String containerName) {
        List<Container> containers = getDockerClient().listContainersCmd()
                .withNameFilter(Collections.singleton(containerName))
                .exec();

        if (containers.isEmpty()) {
            return "";
        }

        return containers.get(0).getId();
    }

}
