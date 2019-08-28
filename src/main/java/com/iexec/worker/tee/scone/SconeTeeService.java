package com.iexec.worker.tee.scone;

import java.util.ArrayList;

import javax.annotation.PreDestroy;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.docker.DockerExecutionConfig;
import com.iexec.worker.docker.DockerExecutionResult;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SconeTeeService {

    // metadata file used by scone enclave. It contains the hash and encryption key
    // for each file in the protected filesystem regions.
    private static final String FSPF_FILENAME = "volume.fspf";

    // beneficiary public key used when encrypting result
    private static final String BENEFICIARY_KEY_FILENAME = "public.key";

    private boolean isLasStarted;

    private SconeLasConfiguration sconeLasConfig;
    private CustomDockerClient customDockerClient;
    private SmsService smsService;

    public SconeTeeService(SconeLasConfiguration sconeLasConfig,
                           CustomDockerClient customDockerClient,
                           SgxService sgxService,
                           SmsService smsService) {

        this.sconeLasConfig = sconeLasConfig;
        this.customDockerClient = customDockerClient;
        this.smsService = smsService;
        isLasStarted = sgxService.isSgxEnabled() ? startLasService() : false;
    }

    public boolean isTeeEnabled() {
        return isLasStarted;
    }

    private boolean startLasService() {
        String chainTaskId = "iexec-las";

        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                .containerName(sconeLasConfig.getContainerName())
                .imageUri(sconeLasConfig.getImageUri())
                .containerPort(sconeLasConfig.getPort())
                .isSgx(true)
                .maxExecutionTime(0)
                .build();

        if (!customDockerClient.pullImage(chainTaskId, sconeLasConfig.getImageUri())) {
            return false;
        }

        DockerExecutionResult dockerExecutionResult = customDockerClient.execute(dockerExecutionConfig);

        if (!dockerExecutionResult.isSuccess()) {
            log.error("Couldn't start LAS service, will continue without TEE support");
            return false;
        }

        return true;
    }

    public String createSconeSecureSession(ContributionAuthorization contributionAuth) {

        // generate secure session
        String sessionId = smsService.getSconeSecureSession(contributionAuth);
        if (sessionId.isEmpty()) {
            return "";
        }

        return sessionId;
    }

    public ArrayList<String> buildSconeDockerEnv(String sconeConfigId, String sconeCasUrl) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfig.getUrl())
                .sconeCasAddress(sconeCasUrl)
                .sconeConfigId(sconeConfigId)
                .build();

        return sconeConfig.toDockerEnv();
    }

    public boolean isEnclaveSignatureValid(String resultHash, String resultSeal,
                                           Signature enclaveSignature, String enclaveAddress) {
        byte[] message = BytesUtils.stringToBytes(HashUtils.concatenateAndHash(resultHash, resultSeal));
        return SignatureUtils.isSignatureValid(message, enclaveSignature, enclaveAddress);
    }

    @PreDestroy
    void stopLasService() {
        if (isLasStarted) {
            customDockerClient.stopAndRemoveContainer(sconeLasConfig.getContainerName());
        }
    }
}