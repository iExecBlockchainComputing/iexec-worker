package com.iexec.worker.tee.scone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.sms.scone.SconeSecureSessionResponse.SconeSecureSession;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.utils.FileHelper;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SconeTeeService {

    private static final String FSPF_FILENAME = "volume.fspf";
    private static final String BENEFICIARY_KEY_FILENAME = "public.key";

    private SconeLasConfiguration sconeLasConfiguration;
    private WorkerConfigurationService workerConfigurationService;
    private PublicConfigurationService publicConfigurationService;
    private SmsService smsService;


    public SconeTeeService(SmsService smsService,
                           SconeLasConfiguration sconeLasConfiguration,
                           WorkerConfigurationService workerConfigurationService,
                           PublicConfigurationService publicConfigurationService) {

        this.smsService = smsService;
        this.sconeLasConfiguration = sconeLasConfiguration;
        this.workerConfigurationService = workerConfigurationService;
        this.publicConfigurationService = publicConfigurationService;
    }

    public String createSconeSecureSession(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();

        // generate secure session
        Optional<SconeSecureSession> oSconeSecureSession = smsService.getSconeSecureSession(contributionAuth);

        if (!oSconeSecureSession.isPresent()) return "";

        SconeSecureSession sconeSecureSession = oSconeSecureSession.get();

        String fspfFilePath = workerConfigurationService.getTaskSconeDir(chainTaskId) + File.separator + FSPF_FILENAME;
        String beneficiaryKeyFilePath = workerConfigurationService.getTaskIexecOutDir(chainTaskId)
                + File.separator + BENEFICIARY_KEY_FILENAME;

        byte[] fspfBytes = Base64.getDecoder().decode(sconeSecureSession.getSconeVolumeFspf());

        FileHelper.createFileWithContent(fspfFilePath, fspfBytes);
        FileHelper.createFileWithContent(beneficiaryKeyFilePath, sconeSecureSession.getBeneficiaryKey());

        return sconeSecureSession.getSessionId();
    }

    public ArrayList<String> buildSconeDockerEnv(String sconeConfigId) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfiguration.getURL())
                .sconeCasAddress(publicConfigurationService.getSconeCasURL())
                .sconeConfigId(sconeConfigId)
                .build();

        return sconeConfig.toDockerEnv();
    }

    public boolean isEnclaveSignatureValid(String resultHash, String resultSeal,
                                           Signature enclaveSignature, String enclaveAddress) {
        byte[] message = BytesUtils.stringToBytes(HashUtils.concatenateAndHash(resultHash, resultSeal));
        return SignatureUtils.isSignatureValid(message, enclaveSignature, enclaveAddress);
    }
}