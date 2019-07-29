package com.iexec.worker.docker;

import com.iexec.common.utils.WaitUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.tee.SgxService;
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

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
                              SconeLasConfiguration sconeLasConfiguration)
                              throws DockerCertificateException {
        docker = DefaultDockerClient.fromEnv().build();

        if (getNetworkListByName(WORKER_DOCKER_NETWORK).isEmpty()) {
            createNetwork(WORKER_DOCKER_NETWORK);
        }

        this.workerConfigurationService = workerConfigurationService;
        this.sconeLasConfiguration = sconeLasConfiguration;
    }

    public String runSgxCheckContainer(DockerExecutionConfig dockerExecutionConfig) {
        String chainTaskId = dockerExecutionConfig.getChainTaskId();
        HostConfig.Bind slashDevBind = createBind("/dev", "/dev");
        HostConfig sgxHostConfig = getSgxHostConfig(slashDevBind);
        ContainerConfig containerConfig = buildContainerConfig(dockerExecutionConfig, sgxHostConfig);

        try {
            return runContainer(chainTaskId, containerConfig, dockerExecutionConfig.getMaxExecutionTime()).get();
        } catch (Exception e) {
            log.error("Could not check SGX device, will run worker without SGX support");
            e.printStackTrace();
            return "";
        }
    }

    public void runLasContainer(DockerExecutionConfig dockerExecutionConfig) {
        String port = dockerExecutionConfig.getContainerPort();
        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            log.error("Cannot run LAS container config, invalid port number [portNumber:{}]", port);
            return;
        }

        HostConfig hostConfig = getSgxHostConfig();
        ContainerConfig containerConfig = buildContainerConfig(dockerExecutionConfig, hostConfig);
        runContainer(dockerExecutionConfig.getChainTaskId(), containerConfig);
    }

    public String runNonTeeTaskContainer(DockerExecutionConfig dockerExecutionConfig) {
        String chainTaskId = dockerExecutionConfig.getChainTaskId();
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);
        HostConfig hostConfig = getHostConfig(inputBind, outputBind);
        ContainerConfig containerConfig = buildContainerConfig(dockerExecutionConfig, hostConfig);

        try {
            return runContainer(chainTaskId, containerConfig, dockerExecutionConfig.getMaxExecutionTime()).get();
        } catch (Exception e) {
            log.error("Could not run container [chainTaskId:{}]", chainTaskId);
            e.printStackTrace();
            return "";
        }
    }

    public String runTeeTaskContainer(DockerExecutionConfig dockerExecutionConfig) {
        String chainTaskId = dockerExecutionConfig.getChainTaskId();
        HostConfig.Bind inputBind = createInputBind(chainTaskId);
        HostConfig.Bind outputBind = createOutputBind(chainTaskId);
        HostConfig.Bind sconeBind = createSconeBind(chainTaskId);
        HostConfig sgxHostConfig = getSgxHostConfig(inputBind, outputBind, sconeBind);
        ContainerConfig containerConfig = buildContainerConfig(dockerExecutionConfig, sgxHostConfig);

        try {
            return runContainer(chainTaskId, containerConfig, dockerExecutionConfig.getMaxExecutionTime()).get();
        } catch (Exception e) {
            log.error("Could not run container [chainTaskId:{}]", chainTaskId);
            e.printStackTrace();
            return "";
        }
    }

    private ContainerConfig buildContainerConfig(DockerExecutionConfig dockerExecutionConfig, HostConfig hostConfig) {
        String imageUri = dockerExecutionConfig.getImageUri();
        String[] cmd = dockerExecutionConfig.getCmd();
        List<String> env = dockerExecutionConfig.getEnv();
        String port = dockerExecutionConfig.getContainerPort();
        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder();

        if (hostConfig == null) {
            return null;
        }

        containerConfigBuilder.hostConfig(hostConfig);
        
        if (imageUri == null || imageUri.isEmpty()) {
            return null;
        }

        containerConfigBuilder.image(imageUri);

        if (cmd != null && cmd.length != 0) {
            containerConfigBuilder.cmd(cmd);
        }

        if (port != null && !port.isEmpty()) {
            containerConfigBuilder.exposedPorts(port);
        }

        if (!env.isEmpty()) {
            containerConfigBuilder.env(env);
        }

        // attach container to "iexec-worker-net" network
        EndpointConfig endpointConfig = EndpointConfig.builder().build();
        Map<String, EndpointConfig> endpointConfigMap = Map.of(WORKER_DOCKER_NETWORK, endpointConfig);
        NetworkingConfig networkingConfig = NetworkingConfig.create(endpointConfigMap);
        containerConfigBuilder.networkingConfig(networkingConfig);

        return containerConfigBuilder.build();

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
        if (source == null || source.isEmpty() || dest == null || dest.isEmpty()) {
            return null;
        }

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

    private HostConfig getHostConfig(HostConfig.Bind... binds) {
        for (HostConfig.Bind bind : binds) {
            if (bind == null) {
                return null;
            }
        }

        return HostConfig.builder()
                .appendBinds(binds)
                .build();
    }

    private HostConfig getSgxHostConfig(HostConfig.Bind... binds) {
        for (HostConfig.Bind bind : binds) {
            if (bind == null) {
                return null;
            }
        }

        Device sgxDevice = Device.builder()
                .pathOnHost(SgxService.SGX_DEVICE_PATH)
                .pathInContainer(SgxService.SGX_DEVICE_PATH)
                .cgroupPermissions(SgxService.SGX_CGROUP_PERMISSIONS)
                .build();

        return HostConfig.builder()
                .appendBinds(binds)
                .devices(sgxDevice)
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
     * This manages the docker execution workflow.
     * We create and start the container.
     * 
     * if (maxExecutionTime == 0)
     *      the container will be considered a service
     *      and will run without a timeout (such as the LAS service)
     * 
     * if (maxExecutionTime != 0)
     *      we wait for the execution to end or we stop the container
     *      when it reaches the deadline.
     * 
     * In the latter case we return the stdout logs as a string.
     */

    public void runContainer(String containerName, ContainerConfig containerConfig) {
        runContainer(containerName, containerConfig, 0);
    }

    @Async
    public CompletableFuture<String> runContainer(String containerName, ContainerConfig containerConfig, long maxExecutionTime) {
        if (containerConfig == null) {
            log.error("Could not run computation, container config is null [chainTaskId:{}]", containerName);
            return CompletableFuture.completedFuture("");
        }

        log.info("Running computation [chainTaskId:{}, image:{}, cmd:{}]",
                containerName, containerConfig.image(), containerConfig.cmd());

        // docker container create
        String containerId = createContainer(containerName, containerConfig);
        if (containerId.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        // docker container start
        boolean isContainerStarted = startContainer(containerId);
        if (!isContainerStarted) {
            removeContainer(containerId);
            return CompletableFuture.completedFuture("");
        }

        if (maxExecutionTime == 0) {
            return CompletableFuture.completedFuture("");
        }

        Date executionTimeoutDate = Date.from(Instant.now().plusMillis(maxExecutionTime));
        waitContainer(containerName, containerId, executionTimeoutDate);
        log.info("Computation completed [chainTaskId:{}]", containerName);

        // docker container stop
        stopContainer(containerId);

        // docker container logs
        String stdout = getContainerLogs(containerId);

        // docker container rm
        removeContainer(containerId);
        return CompletableFuture.completedFuture(stdout);
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

    public void waitContainer(String containerName, String containerId, Date executionTimeoutDate) {
        boolean isComputed = false;
        boolean isTimeout = false;

        if (containerId == null || containerId.isEmpty()) return;

        while (!isComputed && !isTimeout) {
            log.info("Computing [containerName:{}, containerId:{}, status:{}, isComputed:{}, isTimeout:{}]",
                    containerName, containerId, getContainerStatus(containerId), isComputed, isTimeout);

            WaitUtils.sleep(1);
            isComputed = isContainerExited(containerId);
            isTimeout = isAfterTimeout(executionTimeoutDate);
        }

        if (isTimeout) {
            log.warn("Container reached timeout, stopping [containerName:{}, containerId:{}]",
            containerName, containerId);
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

    // This cleans possible conflicting containers having the same name.
    private void removeDuplicateContainer(String containerName) {
        log.debug("Trying to remove duplicate container [containerName:{}]", containerName);

        Container container = getContainerByName(containerName);
        if (container == null) return;

        log.info("Found duplicate container, will remove it [containerName:{}]", containerName);
        removeContainer(container.id());
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

    // This cleans possible conflicting networks having the same name.
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
