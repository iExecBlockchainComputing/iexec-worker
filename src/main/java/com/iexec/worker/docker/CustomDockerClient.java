package com.iexec.worker.docker;

import com.iexec.common.utils.WaitUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.tee.scone.SconeLasConfiguration;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.DockerClient.ListNetworksParam;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.Device;
import com.spotify.docker.client.messages.EndpointConfig;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Network;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.ContainerConfig.NetworkingConfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CustomDockerClient {

    private static final String WORKER_DOCKER_NETWORK = "iexec-worker-net";
    private static final String EXITED = "exited";

    private DefaultDockerClient docker;
    private WorkerConfigurationService workerConfigurationService;
    private SconeLasConfiguration sconeLasConfiguration;

    public CustomDockerClient(WorkerConfigurationService workerConfigurationService,
                              SconeLasConfiguration sconeLasConfiguration) throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();

        if (getNetworkListByName(WORKER_DOCKER_NETWORK).isEmpty()) {
            createNetwork(WORKER_DOCKER_NETWORK);
        }

        this.workerConfigurationService = workerConfigurationService;
        this.sconeLasConfiguration = sconeLasConfiguration;
    }

    public ContainerConfig buildAppContainerConfig(String chainTaskId, String imageUri, List<String> env, String... cmd) {
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);

        if (inputBind == null || outputBind == null) return null;

        HostConfig hostConfig = HostConfig.builder()
                .appendBinds(inputBind, outputBind)
                .build();

        ContainerConfig.Builder commonConfigBuilder =
                commonContainerConfigBuilder(hostConfig, imageUri, env, cmd);

        return commonConfigBuilder != null ? commonConfigBuilder.build() : null;
    }

    public ContainerConfig buildSconeAppContainerConfig(String chainTaskId, String imageUri, List<String> env, String... cmd) {
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);
        HostConfig.Bind sconeBind = createSconeBind(chainTaskId);

        if (inputBind == null || outputBind == null || sconeBind == null) return null;

        HostConfig hostConfig = HostConfig.builder()
                .appendBinds(inputBind, outputBind, sconeBind)
                .devices(getSgxDevice())
                .build();

        ContainerConfig.Builder commonConfigBuilder =
                commonContainerConfigBuilder(hostConfig,imageUri, env, cmd);

        return commonConfigBuilder != null ? commonConfigBuilder.build() : null;
    }

    public ContainerConfig buildSconeLasContainerConfig(String imageUri, String port) {
        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            log.error("Cannot build LAS container config, invalid port number [portNumber:{}]", port);
            return null;
        }

        HostConfig hostConfig = HostConfig.builder()
                .devices(getSgxDevice())
                .build();

        ContainerConfig.Builder commonConfigBuilder =
                commonContainerConfigBuilder(hostConfig, imageUri, List.of(), new String[0]);

        return commonConfigBuilder != null ? commonConfigBuilder.exposedPorts(port).build() : null;
    }

    private ContainerConfig.Builder commonContainerConfigBuilder(HostConfig hostConfig, String imageUri, List<String> env, String... cmd) {
        if (imageUri == null || imageUri.isEmpty()) return null;

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();

        EndpointConfig endpointConfig = EndpointConfig.builder().build();
        Map<String, EndpointConfig> endpointConfigMap = Map.of(WORKER_DOCKER_NETWORK, endpointConfig);
        NetworkingConfig networkingConfig = NetworkingConfig.create(endpointConfigMap);
        containerConfigBuilder.networkingConfig(networkingConfig);

        if (cmd != null && cmd.length != 0) containerConfigBuilder.cmd(cmd);

        return containerConfigBuilder.image(imageUri)
                .hostConfig(hostConfig)
                .env(env);
    }

    private HostConfig.Bind createInputBind(String chainTaskId) {
        String inputMountPoint = workerConfigurationService.getTaskInputDir(chainTaskId);
        return createBind(inputMountPoint, FileHelper.SLASH_IEXEC_IN);
    }

    private HostConfig.Bind createOutputBind(String chainTaskId) {
        String outputMountPoint = workerConfigurationService.getTaskIexecOutDir(chainTaskId);
        return createBind(outputMountPoint, FileHelper.SLASH_IEXEC_OUT);
    }

    private HostConfig.Bind createSconeBind(String chainTaskId) {
        String sconeMountPoint = workerConfigurationService.getTaskSconeDir(chainTaskId);
        return createBind(sconeMountPoint, FileHelper.SLASH_SCONE);
    }

    private HostConfig.Bind createBind(String source, String dest) {
        if (source == null || source.isEmpty() || dest == null || dest.isEmpty()) return null;

        boolean isSourceMountPointSet = FileHelper.createFolder(source);

        if (!isSourceMountPointSet) {
            log.error("Mount point does not exist on host [mountPoint:{}]", source);
            return null;
        }

        return HostConfig.Bind.from(source)
                .to(dest)
                .readOnly(false)
                .build();
    }

    private Device getSgxDevice() {
        return Device.builder()
                .pathOnHost("/dev/isgx")
                .pathInContainer("/dev/isgx")
                .cgroupPermissions("rwm")
                .build();
    }

    public boolean pullImage(String chainTaskId, String image) {
        log.info("Image pull started [chainTaskId:{}, image:{}]", chainTaskId, image);

        try {
            docker.pull(image);
        } catch (DockerException | InterruptedException e) {
            log.error("Image pull failed [chainTaskId:{}, image:{}]", chainTaskId, image);
            e.printStackTrace();
            return false;
        }

        log.info("Image pull completed [chainTaskId:{}, image:{}]", chainTaskId, image);
        return true;
    }

    public boolean isImagePulled(String image) {
        try {
            return !docker.inspectImage(image).id().isEmpty();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to check if image was pulled [image:{}]", image);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This creates a container, starts, waits then stops it, and returns its logs
     */
    public String dockerRun(String chainTaskId, ContainerConfig containerConfig, long maxExecutionTime) {
        if (containerConfig == null) {
            log.error("Could not run computation, container config is null [chainTaskId:{}]", chainTaskId);
            return "";
        }

        log.info("Running computation [chainTaskId:{}, image:{}, cmd:{}]",
                chainTaskId, containerConfig.image(), containerConfig.cmd());

        // docker container create
        String containerId = createContainer(chainTaskId, containerConfig);
        if (containerId.isEmpty()) return "";

        // docker container start
        boolean isContainerStarted = startContainer(containerId);

        if (!isContainerStarted) return "";

        Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
        waitContainer(chainTaskId, containerId, executionTimeoutDate);

        // docker container stop
        stopContainer(containerId);

        log.info("Computation completed [chainTaskId:{}]", chainTaskId);

        // docker container logs
        String stdout = getContainerLogs(containerId);

        // docker container rm
        removeContainer(containerId);
        return stdout;
    }

    public void runContainerAsAService(String containerName, ContainerConfig containerConfig) {
        if (containerConfig == null) {
            log.error("Could not run container as a service, container config is null "
                    + "[containerName:{}]", containerName);
            return;
        }

        log.info("Running container as a service [containerName:{}, image:{}, cmd:{}]",
                containerName, containerConfig.image(), containerConfig.cmd());

        // docker container create
        String containerId = createContainer(containerName, containerConfig);
        if (containerId.isEmpty()) return;

        // docker container start
        startContainer(containerId);
    }

    private Container getContainerByName(String containerName) {
        log.debug("Getting container by name [containerName:{}]", containerName);
        List<Container> containerList;

        try {
            containerList = docker.listContainers(ListContainersParam.allContainers())
                    .stream()
                    .filter(container -> container.names().contains("/" + containerName))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get container by name [containerName:{}]", containerName);
            e.printStackTrace();
            return null;
        }

        if (containerList.isEmpty()) return null;
        return containerList.get(0);
    }

    public String createContainer(String containerName, ContainerConfig containerConfig) {
        log.debug("Creating container [containerName:{}]", containerName);

        if (containerConfig == null) return "";
        removeDuplicateContainer(containerName);
        ContainerCreation containerCreation;

        try {
            containerCreation = docker.createContainer(containerConfig, containerName);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to create container [containerName:{}, image:{}, cmd:{}]",
                    containerName, containerConfig.image(), containerConfig.cmd());
            e.printStackTrace();
            return "";
        }

        if (containerCreation == null) return "";

        log.info("Created container [containerName:{}, containerId:{}]",
                containerName, containerCreation.id());

        return containerCreation.id() != null ? containerCreation.id() : "";
    }

    public boolean startContainer(String containerId) {
        log.debug("Starting container [containerId:{}]", containerId);

        try {
            docker.startContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to start container [containerId:{}]", containerId);
            e.printStackTrace();
            removeContainer(containerId);
            return false;
        }

        log.debug("Started container [containerId:{}]", containerId);
        return true;
    }

    public void waitContainer(String chainTaskId, String containerId, Date executionTimeoutDate) {
        boolean isComputed = false;
        boolean isTimeout = false;

        if (containerId == null || containerId.isEmpty()) return;

        while (!isComputed && !isTimeout) {
            log.info("Computing [chainTaskId:{}, containerId:{}, status:{}, isComputed:{}, isTimeout:{}]",
                    chainTaskId, containerId, getContainerStatus(containerId), isComputed, isTimeout);

            WaitUtils.sleep(1);
            isComputed = isContainerExited(containerId);
            isTimeout = isAfterTimeout(executionTimeoutDate);
        }

        if (isTimeout) {
            log.warn("Container reached timeout, stopping [chainTaskId:{}, containerId:{}]", chainTaskId, containerId);
        }
    }

    public boolean stopContainer(String containerId) {
        log.debug("Stopping container [containerId:{}]", containerId);

        try {
            docker.stopContainer(containerId, 0);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to stop container [containerId:{}]", containerId);
            e.printStackTrace();
            return false;
        }

        log.debug("Stopped container [containerId:{}]", containerId);
        return true;
    }

    public String getContainerLogs(String containerId) {
        log.debug("Getting container logs [containerId:{}]", containerId);
        String stdout = "";

        try {
            stdout = docker.logs(containerId, LogsParam.stdout(), LogsParam.stderr()).readFully();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container logs [containerId:{}]", containerId);
            e.printStackTrace();
            return "Failed to get computation logs";
        }

        log.debug("Got container logs [containerId:{}]", containerId);
        return stdout;
    }

    public boolean removeContainer(String containerId) {
        log.debug("Removing container [containerId:{}]", containerId);
        try {
            docker.removeContainer(containerId);
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to remove container [containerId:{}]", containerId);
            e.printStackTrace();
            return false;
        }

        log.debug("Removed container [containerId:{}]", containerId);
        return true;
    }

    private boolean removeContainerByName(String containerName) {
        Container container = getContainerByName(containerName);
        return removeContainer(container.id());
    }

    private void removeDuplicateContainer(String containerName) {
        log.debug("Trying to remove duplicate container [containerName:{}]", containerName);

        Container container = getContainerByName(containerName);
        if (container == null) return;

        log.info("Found duplicate container, will remove it [containerName:{}]", containerName);
        removeContainer(container.id());
    }

    public boolean isContainerExited(String containerId) {
        return getContainerStatus(containerId).equals(EXITED);
    }

    private String getContainerStatus(String containerId) {
        try {
            return docker.inspectContainer(containerId).state().status();
        } catch (DockerException | InterruptedException e) {
            log.error("Failed to get container status [containerId:{}]", containerId);
            e.printStackTrace();
            return "";
        }
    }

    private boolean isAfterTimeout(Date executionTimeoutDate) {
        return new Date().after(executionTimeoutDate);
    }

    // we may have multiple networks having the same name
    // but different IDs.
    private List<Network> getNetworkListByName(String networkName) {
        log.debug("Getting network list by name [networkName:{}]", networkName);

        try {
            return docker.listNetworks(ListNetworksParam.byNetworkName(networkName));
        } catch (Exception e) {
            log.error("Failed to get network list by name [networkName:{}]", networkName);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private String createNetwork(String networkName) {
        log.debug("Creating network [networkName:{}]", networkName);
        NetworkConfig networkConfig = NetworkConfig.builder()
                .driver("bridge")
                .checkDuplicate(true)
                .name(networkName)
                .build();

        String networkId;
        try {
            networkId = docker.createNetwork(networkConfig).id();
        } catch (Exception e) {
            log.error("Failed to create docker network [name:{}]", networkName);
            e.printStackTrace();
            return "";
        }

        log.debug("Created network [networkName:{}, networkId]", networkName, networkId);
        return networkId;
    }

    private void removeNetwork(String networkId) {
        log.debug("Removing network [networkId:{}]", networkId);
        try {
            docker.removeNetwork(networkId);
            log.debug("Removed network [networkId:{}]", networkId);
        } catch (Exception e) {
            log.error("Failed to remove docker network [networkId:{}]", networkId);
            e.printStackTrace();
        }
    }

    // TODO: clean all containers connecting to
    // the network before removing it
    @PreDestroy
    void onPreDestroy() {
        removeContainerByName(sconeLasConfiguration.getContainerName());
        for (Network network : getNetworkListByName(WORKER_DOCKER_NETWORK)) {
            removeNetwork(network.id());
        }
        docker.close();
    }
}
