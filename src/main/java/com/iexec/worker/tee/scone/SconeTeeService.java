package com.iexec.worker.tee.scone;

import java.util.List;
import java.util.Optional;

import javax.annotation.PreDestroy;

import com.iexec.worker.compute.DockerService;
import com.iexec.worker.compute.DockerCompute;
import com.iexec.worker.sgx.SgxService;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SconeTeeService {

    private SconeLasConfiguration sconeLasConfig;
    private DockerService dockerService;
    private boolean isLasStarted;

    public SconeTeeService(SconeLasConfiguration sconeLasConfig,
                           DockerService dockerService,
                           SgxService sgxService) {
        this.sconeLasConfig = sconeLasConfig;
        this.dockerService = dockerService;
        isLasStarted = sgxService.isSgxEnabled() ? startLasService() : false;
    }

    public boolean isTeeEnabled() {
        return isLasStarted;
    }

    private boolean startLasService() {
        String chainTaskId = "iexec-las";

        DockerCompute dockerCompute = DockerCompute.builder()
                .chainTaskId(chainTaskId)
                .containerName(sconeLasConfig.getContainerName())
                .imageUri(sconeLasConfig.getImageUri())
                .containerPort(sconeLasConfig.getPort())
                .isSgx(true)
                .maxExecutionTime(0)
                .build();

        if (!dockerService.pullImage(chainTaskId, sconeLasConfig.getImageUri())) {
            return false;
        }

        Optional<String> oStdout = dockerService.run(dockerCompute);

        if (oStdout.isEmpty()) {
            log.error("Couldn't start LAS service, will continue without TEE support");
        }

        return oStdout.isPresent();
    }

    public List<String> buildSconeDockerEnv(String sconeConfigId, String sconeCasUrl, String sconeHeap) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeCasUrl)
                .sconeConfigId(sconeConfigId)
                .sconeHeap(sconeHeap)
                .build();

        return sconeConfig.toDockerEnv();
    }

    @PreDestroy
    void stopLasService() {
        if (isLasStarted) {
            dockerService.stopAndRemoveContainer(sconeLasConfig.getContainerName());
        }
    }
}