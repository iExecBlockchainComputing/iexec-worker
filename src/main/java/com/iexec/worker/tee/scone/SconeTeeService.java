package com.iexec.worker.tee.scone;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import javax.annotation.PreDestroy;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.sms.scone.SconeSecureSessionResponse.SconeSecureSession;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.docker.DockerExecutionConfig;
import com.iexec.worker.docker.DockerExecutionResult;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.utils.FileHelper;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SconeTeeService {

    // metadata file used by scone enclave. It contains the hash and encryption key
    // for each file in the protected filesystem regions.
    private static final String FSPF_FILENAME = "volume.fspf";
    private static final String BENEFICIARY_KEY_FILENAME = "public.key";

    private boolean isLasStarted;

    private SconeLasConfiguration sconeLasConfiguration;
    private CustomDockerClient customDockerClient;
    private SmsService smsService;
    private SgxService sgxService;

    public SconeTeeService(SconeLasConfiguration sconeLasConfiguration,
                           CustomDockerClient customDockerClient,
                           SgxService sgxService,
                           SmsService smsService) {

        this.sconeLasConfiguration = sconeLasConfiguration;
        this.customDockerClient = customDockerClient;
        this.smsService = smsService;
        this.sgxService = sgxService;
    }

    public boolean isLasStarted() {
        isLasStarted = startLasService();
        return isLasStarted;
    }

    public boolean startLasService() {
        isLasStarted = false;

        if (!sgxService.isSgxEnabled()) {
            return false;
        }

        String chainTaskId = "iexec-las";
        DockerExecutionConfig dockerExecutionConfig = DockerExecutionConfig.builder()
                .chainTaskId(chainTaskId)
                // don't add containerName here it creates conflict
                // when running multiple workers on the same machine
                .imageUri(sconeLasConfiguration.getImageUri())
                .containerPort(sconeLasConfiguration.getPort())
                .isSgx(true)
                .maxExecutionTime(0)
                .build();

        if (!customDockerClient.pullImage(chainTaskId, sconeLasConfiguration.getImageUri())) {
            return false;
        }

        DockerExecutionResult dockerExecutionResult = customDockerClient.execute(dockerExecutionConfig);

        if (!dockerExecutionResult.isSuccess()) {
            log.error("Couldn't start LAS service, will continue without TEE support");
            return false;
        }

        sconeLasConfiguration.setContainerName(dockerExecutionResult.getContainerName());
        isLasStarted = true;
        return true;
    }

    public String createSconeSecureSession(ContributionAuthorization contributionAuth,
                                           String fspfFolderPath,
                                           String beneficiaryKeyFolderPath) {

        String chainTaskId = contributionAuth.getChainTaskId();
        String fspfFilePath = fspfFolderPath + File.separator + FSPF_FILENAME;
        String beneficiaryKeyFilePath = beneficiaryKeyFolderPath + File.separator + BENEFICIARY_KEY_FILENAME;

        // generate secure session
        Optional<SconeSecureSession> oSconeSecureSession = smsService.getSconeSecureSession(contributionAuth);
        if (!oSconeSecureSession.isPresent()) {
            return "";
        }

        SconeSecureSession sconeSecureSession = oSconeSecureSession.get();

        byte[] fspfBytes = Base64.getDecoder().decode(sconeSecureSession.getSconeVolumeFspf());
        String sessionId = sconeSecureSession.getSessionId();
        String beneficiaryKey = sconeSecureSession.getBeneficiaryKey();

        File fspfFile = FileHelper.createFileWithContent(fspfFilePath, fspfBytes);
        File keyFile =  FileHelper.createFileWithContent(beneficiaryKeyFilePath,beneficiaryKey);

        if (!fspfFile.exists() || !keyFile.exists()) {
            log.error("Problem writing scone secure session files "
                    + "[chainTaskId:{}, sessionId:{}, fspfFileExists:{}, keyFileExists:{}]",
                    chainTaskId, sessionId, fspfFile.exists(), keyFile.exists());
            return "";
        }

        return sessionId;
    }

    public ArrayList<String> buildSconeDockerEnv(String sconeConfigId, String sconeCasUrl) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfiguration.getUrl())
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
            customDockerClient.stopAndRemoveContainer(sconeLasConfiguration.getContainerName());
        }
    }
}