package com.iexec.worker.tee.scone;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.sms.scone.SconeSecureSessionResponse.SconeSecureSession;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.utils.FileHelper;

import org.springframework.stereotype.Service;


@Service
public class SconeTeeService {

    private static final String FSPF_FILENAME = "volume.fspf";
    private static final String BENEFICIARY_KEY_FILENAME = "public.key";

    private SmsService smsService;
    private SconeLasConfiguration sconeLasConfiguration;
    private WorkerConfigurationService workerConfigurationService;
    private PublicConfigurationService publicConfigurationService;


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
        Optional<SconeSecureSession> oSmsSecureSession = smsService.getSconeSecureSession(contributionAuth);

        if (!oSmsSecureSession.isPresent()) return "";

        SconeSecureSession smsSecureSession = oSmsSecureSession.get();

        String fspfFilePath = workerConfigurationService.getTaskSconeDir(chainTaskId) + File.separator + FSPF_FILENAME;
        String beneficiaryKeyFilePath = workerConfigurationService.getTaskIexecOutDir(chainTaskId)
                + File.separator + BENEFICIARY_KEY_FILENAME;

        byte[] fspfBytes = Base64.getDecoder().decode(smsSecureSession.getSconeVolumeFspf());

        FileHelper.createFileWithContent(fspfFilePath, fspfBytes);
        FileHelper.createFileWithContent(beneficiaryKeyFilePath, smsSecureSession.getBeneficiaryKey());

        return smsSecureSession.getSessionId();
    }

    public ArrayList<String> buildSconeDockerEnv(String sconeConfigId) {
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfiguration.getURL())
                .sconeCasAddress(publicConfigurationService.getSconeCasURL())
                .sconeConfigId(sconeConfigId)
                .build();

        return sconeConfig.toDockerEnv();
    }
}