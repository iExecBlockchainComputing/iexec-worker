package com.iexec.worker.executor;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.sms.tee.SmsSecureSessionResponse.SmsSecureSession;
import com.iexec.common.tee.scone.SconeConfig;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.CustomDockerClient;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeLasConfiguration;
import com.iexec.worker.utils.FileHelper;
import org.apache.commons.lang3.tuple.Pair;
import com.spotify.docker.client.messages.ContainerConfig;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import static com.iexec.common.replicate.ReplicateStatus.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
public class ComputationService {

    private SmsService smsService;
    private SconeLasConfiguration sconeLasConfiguration;
    private DatasetService datasetService;
    private DockerComputationService dockerComputationService;
    private CustomDockerClient customDockerClient;
    private WorkerConfigurationService workerConfigurationService;
    private PublicConfigurationService publicConfigurationService;

    public ComputationService(SmsService smsService,
                              SconeLasConfiguration sconeLasConfiguration,
                              DatasetService datasetService,
                              DockerComputationService dockerComputationService,
                              CustomDockerClient customDockerClient,
                              WorkerConfigurationService workerConfigurationService,
                              PublicConfigurationService publicConfigurationService) {

        this.smsService = smsService;
        this.sconeLasConfiguration = sconeLasConfiguration;
        this.datasetService = datasetService;
        this.dockerComputationService = dockerComputationService;
        this.customDockerClient = customDockerClient;
        this.workerConfigurationService = workerConfigurationService;
        this.publicConfigurationService = publicConfigurationService;
    }


    public Pair<ReplicateStatus, String> runTeeComputation(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();
        String stdout;

        // generate secure session
        Optional<SmsSecureSession> oSmsSecureSession = smsService.generateTaskSecureSession(contributionAuth);
        if (!oSmsSecureSession.isPresent()) {
            stdout = "Could not generate secure session for tee computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        SmsSecureSession smsSecureSession = oSmsSecureSession.get();
        log.info("smsSecureSession: {}", smsSecureSession);

        String fspfFilePath = workerConfigurationService.getTaskOutputDir(chainTaskId) + "/volume.fspf";
        String beneficiaryKeyFilePath = workerConfigurationService.getTaskOutputDir(chainTaskId) + "/public.key";

        byte[] fspfBytes = Base64.getDecoder().decode(smsSecureSession.getOutputFspf());

        FileHelper.createFileWithContent(fspfFilePath, fspfBytes);
        FileHelper.createFileWithContent(beneficiaryKeyFilePath, smsSecureSession.getBeneficiaryKey());

        // get host config
        String hostBaseVolume = workerConfigurationService.getTaskBaseDir(chainTaskId);
        // HostConfig hostConfig = CustomDockerClient.getHostConfig(hostBaseVolume);

        // build tee container config
        SconeConfig sconeConfig = SconeConfig.builder()
                .sconeLasAddress(sconeLasConfiguration.getURL())
                .sconeCasAddress(publicConfigurationService.getSconeCasURL())
                .sconeConfigId(smsSecureSession.getSessionId() + "/app")
                .build();

        String datasetFilename = datasetService.getDatasetFilename(replicateModel.getDatasetUri());
        List<String> dockerEnvVariables = new ArrayList<>(sconeConfig.toDockerEnv());
        dockerEnvVariables.add("DATASET_FILENAME=" + datasetFilename);

        ContainerConfig teeContainerConfig = customDockerClient.buildTeeContainerConfig(hostBaseVolume, replicateModel.getAppUri(),
                replicateModel.getCmd(), dockerEnvVariables);

        stdout = dockerComputationService.dockerRunAndGetLogs(teeContainerConfig, chainTaskId, replicateModel.getMaxExecutionTime());

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        return Pair.of(COMPUTED, stdout);        

        // customDockerClient.buildTeeContainerConfig(hostBaseVolume, replicateModel.getAppUri(),
        //         replicateModel.getCmd(), "SCONE_CAS_ADDR=<IP DE TON SMS>:18765", "SCONE_LAS_ADDR=127.0.0.1:18766",
        //         "SCONE_HEAP=1G", "SCONE_LOG=7", "SCONE_VERSION=1", "SCONE_CONFIG_ID=$NAME/app");

        // containerConfig = getContainerConfig(image, replicateModel.getCmd(), hostBaseVolume,
        //             TEE_DOCKER_ENV_CHAIN_TASKID + "=" + chainTaskId,
        //             TEE_DOCKER_ENV_WORKER_ADDRESS + "=" + configurationService.getWorkerWalletAddress(),
        //             DATASET_FILENAME + "=" + datasetFilename);
        // dockerRun tee container
        // return logs


        // docker run  \
        //     --network=host \
        //     -e "SCONE_CAS_ADDR=<IP DE TON SMS>:18765" \
        //     -e "SCONE_LAS_ADDR=127.0.0.1:18766" \
        //     -e "SCONE_HEAP=1G" \
        //     -e SCONE_LOG=7 \
        //     -e SCONE_VERSION=1 \
        //     -e "SCONE_CONFIG_ID=$NAME/app" \     # c'est quoi $NAME ?
        //     -v $PWD/data:/data \                 # ce volume equivalent a iexec_in ?
        //     -v $PWD/output:/output \             # et celui la equivalent a iexec_out ?
        //     test                                 # tu peut me l'envoyer ?
    }

    public Pair<ReplicateStatus, String> runNonTeeComputation(AvailableReplicateModel replicateModel) {
        ContributionAuthorization contributionAuth = replicateModel.getContributionAuthorization();
        String chainTaskId = contributionAuth.getChainTaskId();
        String stdout = "";

        // fetch task secrets from SMS
        boolean isFetched = smsService.fetchTaskSecrets(contributionAuth);
        if (!isFetched) {
            log.warn("No secrets fetched for this task, will continue [chainTaskId:{}]:", chainTaskId);
        }

        // decrypt data
        boolean isDatasetDecryptionNeeded = datasetService.isDatasetDecryptionNeeded(chainTaskId);
        boolean isDatasetDecrypted = false;

        if (isDatasetDecryptionNeeded) {
            isDatasetDecrypted = datasetService.decryptDataset(chainTaskId, replicateModel.getDatasetUri());
        }

        if (isDatasetDecryptionNeeded && !isDatasetDecrypted) {
            stdout = "Failed to decrypt dataset, URI:" + replicateModel.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        // compute
        String datasetFilename = datasetService.getDatasetFilename(replicateModel.getDatasetUri());
        stdout = dockerComputationService.dockerRunAndGetLogs(replicateModel, datasetFilename);

        if (stdout.isEmpty()) {
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return Pair.of(COMPUTE_FAILED, stdout);
        }

        return Pair.of(COMPUTED, stdout);        
    }
}