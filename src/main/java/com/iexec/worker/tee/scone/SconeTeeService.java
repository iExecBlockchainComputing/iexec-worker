package com.iexec.worker.tee.scone;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.sms.tee.SmsSecureSessionResponse.SmsSecureSession;
import com.iexec.common.tee.scone.SconeConfig;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.utils.FileHelper;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.HostConfig;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
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

    public void buildSconeEnv() {
        
    }

    public ContainerConfig buildSconeContainerConfig(String secureSessionId, AvailableReplicateModel replicateModel) {
        String chainTaskId = replicateModel.getContributionAuthorization().getChainTaskId();
        String appUri = replicateModel.getAppUri();
        String cmd = replicateModel.getCmd();

        // get host config
        String hostBaseVolume = workerConfigurationService.getTaskBaseDir(chainTaskId);
        HostConfig hostConfig = CustomDockerClient.getHostConfig(hostBaseVolume);

        if (appUri.isEmpty() || hostConfig == null) return null;

        // build tee container config
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfiguration.getURL())
                .sconeCasAddress(publicConfigurationService.getSconeCasURL())
                .sconeConfigId(secureSessionId + "/app")
                .build();

        String datasetFilename = FileHelper.getFilenameFromUri(replicateModel.getDatasetUri());
        List<String> dockerEnvVariables = new ArrayList<>(sconeConfig.toDockerEnv());
        dockerEnvVariables.add("DATASET_FILENAME=" + datasetFilename);

        ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder()
                .image(appUri)
                .hostConfig(hostConfig);

        if (cmd != null && !cmd.isEmpty()) containerConfigBuilder.cmd(cmd);

        return containerConfigBuilder.env(dockerEnvVariables).build();
    }

    public String createSconeSecureSession(ContributionAuthorization contributionAuth) {
        String chainTaskId = contributionAuth.getChainTaskId();

        // generate secure session
        Optional<SmsSecureSession> oSmsSecureSession = smsService.generateTaskSecureSession(contributionAuth);

        if (!oSmsSecureSession.isPresent()) return "";

        SmsSecureSession smsSecureSession = oSmsSecureSession.get();
        log.info("smsSecureSession: {}", smsSecureSession);

        String fspfFilePath = workerConfigurationService.getTaskOutputDir(chainTaskId)
                + File.separator + FSPF_FILENAME;

        String beneficiaryKeyFilePath = workerConfigurationService.getTaskOutputDir(chainTaskId)
                + File.separator + BENEFICIARY_KEY_FILENAME;

        byte[] fspfBytes = Base64.getDecoder().decode(smsSecureSession.getOutputFspf());

        FileHelper.createFileWithContent(fspfFilePath, fspfBytes);
        FileHelper.createFileWithContent(beneficiaryKeyFilePath, smsSecureSession.getBeneficiaryKey());

        return smsSecureSession.getSessionId();
    }
}