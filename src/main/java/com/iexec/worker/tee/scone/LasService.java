package com.iexec.worker.tee.scone;

import com.github.dockerjava.api.model.HostConfig;
import com.iexec.commons.containers.DockerRunRequest;
import com.iexec.commons.containers.DockerRunResponse;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sgx.SgxService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LasService {
    @Getter
    private final String containerName;
    private final String imageUri;
    @Getter
    private final SconeConfiguration sconeConfig;

    private final WorkerConfigurationService workerConfigService;
    private final SgxService sgxService;
    private final DockerService dockerService;

    @Getter
    private boolean isStarted;

    public LasService(String containerName,
                      String imageUri,
                      SconeConfiguration sconeConfig,
                      WorkerConfigurationService workerConfigService,
                      SgxService sgxService,
                      DockerService dockerService) {
        this.containerName = containerName;
        this.imageUri = imageUri;
        this.sconeConfig = sconeConfig;
        this.workerConfigService = workerConfigService;
        this.sgxService = sgxService;
        this.dockerService = dockerService;
    }

    synchronized boolean start() {
        if (isStarted) {
            return true;
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withDevices(sgxService.getSgxDevices())
                .withNetworkMode(workerConfigService.getDockerNetworkName());
        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .hostConfig(hostConfig)
                .containerName(containerName)
                .imageUri(imageUri)
                // pre-compute, application & post-compute enclaves will be
                // able to talk to the LAS via this network
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .maxExecutionTime(0)
                .build();
        if (!imageUri.contains(sconeConfig.getRegistryName())) {
            log.error("LAS image is not from a known registry [image:{}, registry:{}]",
                    imageUri, sconeConfig.getRegistryName());
            return false;
        }
        DockerClientInstance client;
        try {
            client = dockerService.getClient(
                    sconeConfig.getRegistryName(),
                    sconeConfig.getRegistryUsername(),
                    sconeConfig.getRegistryPassword());
        } catch (Exception e) {
            log.error("Failed to get Docker authenticated client to run LAS", e);
            return false;
        }
        if (client == null) {
            log.error("Docker client with credentials is required to enable TEE support");
            return false;
        }
        if (!client.pullImage(imageUri)) {
            log.error("Failed to download LAS image");
            return false;
        }

        DockerRunResponse dockerRunResponse = dockerService.run(dockerRunRequest);
        if (!dockerRunResponse.isSuccessful()) {
            log.error("Failed to start LAS service");
            return false;
        }

        isStarted = true;
        return true;
    }

    /**
     * Tries to stop and remove this LAS instance container.
     * It is considered successful when the container is not present anymore
     * after the execution of this method.
     *
     * @return {@literal true} if the container is not present anymore,
     * {@literal false} otherwise.
     */
    synchronized boolean stopAndRemoveContainer() {
        if (isStarted()) {
            final DockerClientInstance client = dockerService.getClient();
            client.stopAndRemoveContainer(containerName);
            // TODO: this should be the default case in `DockerClientInstance::stopAndRemoveContainer`
            isStarted = client.isContainerPresent(containerName);
        }

        return !isStarted;
    }

    public String getUrl() {
        return containerName + ":" + sconeConfig.getLasPort();
    }
}
