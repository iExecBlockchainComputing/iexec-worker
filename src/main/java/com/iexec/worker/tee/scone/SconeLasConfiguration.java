package com.iexec.worker.tee.scone;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.iexec.worker.config.WorkerConfigurationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LAS: local attestation service.
 * Local service used to perform SGX specific operations to attest the enclave
 * (eg compute enclave measurement - MREnclave - and attest it through Intel
 * Attestation Service).
 * It must be on the same machine as the attested program/enclave.
 * 
 * MREnclave: an enclave identifier, created by hashing all its
 * code. It guarantees that a code behaves exactly as expected.
 */
@Component
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SconeLasConfiguration {

    @Value("${scone.las.image}")
    private String image;

    @Value("${scone.las.version}")
    private String version;

    @Value("${scone.las.port}")
    private String port;

    private String containerName;

    private WorkerConfigurationService workerConfigService;

    public SconeLasConfiguration(WorkerConfigurationService workerConfigService) {
        // We use "worker-name-iexec-las" as containerName to avoid conflict
        // when running multiple workers on the same machine.
        containerName = workerConfigService.getWorkerName() + "-iexec-las";
    }

    public String getContainerName() {
        return containerName;
    }

    public String getImageUri() {
        return image + ":" + version;
    }

    public String getUrl() {
        return containerName + ":" + port;
    }
}