package com.iexec.worker.tee.scone;

import com.iexec.worker.config.WorkerConfigurationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
@Service
public class SconeLasConfiguration {

    @Value("${scone.las.image}")
    private String image;

    @Value("${scone.las.version}")
    private String version;

    @Value("${scone.las.port}")
    private String port;

    private String containerName;

    public SconeLasConfiguration(WorkerConfigurationService workerConfigService) {
        // "iexec-las-0xWalletAddress" as containerName to avoid naming conflict
        // when running multiple workers on the same machine.
        containerName = "iexec-las-" + workerConfigService.getWorkerWalletAddress();
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

    public String getPort() {
        return port;
    }
}