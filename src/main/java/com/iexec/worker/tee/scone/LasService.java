package com.iexec.worker.tee.scone;

import com.iexec.common.docker.DockerRunRequest;
import com.iexec.common.docker.DockerRunResponse;
import com.iexec.common.docker.client.DockerClientInstance;
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

    boolean start() {
        if (isStarted) {
            return true;
        }

        DockerRunRequest dockerRunRequest = DockerRunRequest.builder()
                .containerName(containerName)
                .imageUri(imageUri)
                // application & post-compose enclaves will be
                // able to talk to the LAS via this network
                .dockerNetwork(workerConfigService.getDockerNetworkName())
                .sgxDriverMode(sgxService.getSgxDriverMode())
                .maxExecutionTime(0)
                .build();
        if (!imageUri.contains(sconeConfig.getRegistryName())) {
            throw new RuntimeException(String.format("LAS image (%s) is not " +
                    "from a known registry (%s)", imageUri, sconeConfig.getRegistryName()));
        }
        DockerClientInstance client;
        try {
            client = dockerService.getClient(
                    sconeConfig.getRegistryName(),
                    sconeConfig.getRegistryUsername(),
                    sconeConfig.getRegistryPassword());
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException("Failed to get Docker authenticated client to run LAS");
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
        return true;
    }

    void stop() {
        if (isStarted) {
            dockerService.getClient().stopAndRemoveContainer(containerName);
        }
    }

    public String getUrl() {
        return containerName + ":" + sconeConfig.getLasPort();
    }
}
