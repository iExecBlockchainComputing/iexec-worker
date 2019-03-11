package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.DockerComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.feign.ResultRepoClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.security.TeeSignature;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.replicate.ReplicateStatus.*;


/* 
 * this service is only caller by ReplicateDemandService when getting new replicate
 * or by AmnesiaRecoveryService when recovering an interrupted task
 */
@Slf4j
@Service
public class TaskExecutorService {

    // external services
    private DatasetService datasetService;
    private DockerComputationService dockerComputationService;
    private ResultService resultService;
    private ContributionService contributionService;
    private CustomFeignClient customFeignClient;
    private ResultRepoClient resultRepoClient;
    private RevealService revealService;
    private PublicConfigurationService publicConfigurationService;
    private CredentialsService credentialsService;

    // internal variables
    private String workerWalletAddress;
    private int maxNbExecutions;
    private ThreadPoolExecutor executor;

    public TaskExecutorService(DatasetService datasetService,
                               DockerComputationService dockerComputationService,
                               ResultService resultService,
                               ContributionService contributionService,
                               CustomFeignClient customFeignClient,
                               ResultRepoClient resultRepoClient,
                               RevealService revealService,
                               PublicConfigurationService publicConfigurationService,
                               CredentialsService credentialsService,
                               WorkerConfigurationService workerConfigurationService) {
        this.datasetService = datasetService;
        this.dockerComputationService = dockerComputationService;
        this.resultService = resultService;
        this.contributionService = contributionService;
        this.customFeignClient = customFeignClient;
        this.resultRepoClient = resultRepoClient;
        this.revealService = revealService;
        this.customFeignClient = customFeignClient;
        this.publicConfigurationService = publicConfigurationService;
        this.credentialsService = credentialsService;

        this.workerWalletAddress = workerConfigurationService.getWorkerWalletAddress();
        maxNbExecutions = Runtime.getRuntime().availableProcessors() - 1;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxNbExecutions);
    }

    public boolean canAcceptMoreReplicates() {
        return executor.getActiveCount() < maxNbExecutions;
    }

    public void addReplicate(ContributionAuthorization contributionAuth, AvailableReplicateModel replicateModel) {
        String chainTaskId = replicateModel.getContributionAuthorization().getChainTaskId();

        CompletableFuture.supplyAsync(() -> executeTask(chainTaskId, replicateModel), executor)     // compute
                .thenApply(stdout -> resultService.saveResult(chainTaskId, replicateModel, stdout)) // save result
                .thenAccept(isSaved -> {if (isSaved) tryToContribute(contributionAuth);})           // contribute
                .handle((res, err) -> {                                                             // handle errors
                    if (err != null) {
                        log.error(err.getMessage());
                        return null;
                    }
                    return res;
                });
    }

    @Async
    private String executeTask(String chainTaskId, AvailableReplicateModel replicateModel) {
        String stdout = "";

        // check app type
        customFeignClient.updateReplicateStatus(chainTaskId, RUNNING);
        if (!replicateModel.getAppType().equals(DappType.DOCKER)) {
            stdout = "Application is not of type Docker";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        // pull app
        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADING);
        boolean isAppDownloaded = dockerComputationService.dockerPull(chainTaskId, replicateModel.getAppUri());
        if (!isAppDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOAD_FAILED);
            stdout = "Failed to pull application image, URI:" + replicateModel.getAppUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, APP_DOWNLOADED);

        // pull data
        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADING);
        boolean isDatasetDownloaded = datasetService.downloadDataset(chainTaskId, replicateModel.getDatasetUri());
        if (!isDatasetDownloaded) {
            customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOAD_FAILED);
            stdout = "Failed to pull dataset, URI:" + replicateModel.getDatasetUri();
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, DATA_DOWNLOADED);

        // compute
        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTING);
        stdout = dockerComputationService.dockerRunAndGetLogs(replicateModel);

        if (stdout.isEmpty()) {
            customFeignClient.updateReplicateStatus(chainTaskId, COMPUTE_FAILED);
            stdout = "Failed to start computation";
            log.error(stdout + " [chainTaskId:{}]", chainTaskId);
            return stdout;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, COMPUTED);
        return stdout;
    }

    @Async
    public void tryToContribute(ContributionAuthorization contribAuth) {
        String deterministHash = resultService.getDeterministHashFromFile(contribAuth.getChainTaskId());
        Optional<TeeSignature.Sign> enclaveSignature = resultService.getEnclaveSignatureFromFile(contribAuth.getChainTaskId());

        if (deterministHash.isEmpty()) {
            return;
        }
        String chainTaskId = contribAuth.getChainTaskId();
        Sign.SignatureData enclaveSignatureData = contributionService.getEnclaveSignatureData(contribAuth, deterministHash, enclaveSignature);

        Optional<ReplicateStatus> canContributeStatus = contributionService.getCanContributeStatus(chainTaskId);
        if (!canContributeStatus.isPresent()) {
            log.error("canContributeStatus should not be empty (getChainTask issue) [chainTaskId:{}]", chainTaskId);
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, canContributeStatus.get());
        if (!canContributeStatus.get().equals(CAN_CONTRIBUTE) & enclaveSignatureData != null) {
            log.warn("Cant contribute [chainTaskId:{}, status:{}]", chainTaskId, canContributeStatus.get());
            return;
        }

        if (!contributionService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTING);

        ChainReceipt chainReceipt = contributionService.contribute(contribAuth, deterministHash, enclaveSignatureData);
        if (chainReceipt == null) {
            customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTE_FAILED);
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, CONTRIBUTED, chainReceipt);
    }

    @Async
    public void reveal(String chainTaskId) {
        log.info("Trying to reveal [chainTaskId:{}]", chainTaskId);
        if (!revealService.canReveal(chainTaskId)) {
            log.warn("The worker will not be able to reveal [chainTaskId:{}]", chainTaskId);
            customFeignClient.updateReplicateStatus(chainTaskId, CANT_REVEAL);
        }

        if (!revealService.hasEnoughGas()) {
            customFeignClient.updateReplicateStatus(chainTaskId, OUT_OF_GAS);
            System.exit(0);
        }

        customFeignClient.updateReplicateStatus(chainTaskId, REVEALING);

        Optional<ChainReceipt> optionalChainReceipt = revealService.reveal(chainTaskId);
        if (!optionalChainReceipt.isPresent()) {
            customFeignClient.updateReplicateStatus(chainTaskId, REVEAL_FAILED);
            return;
        }

        customFeignClient.updateReplicateStatus(chainTaskId, REVEALED, optionalChainReceipt.get());
    }

    public void abortConsensusReached(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONSENSUS_REACHED);
    }

    public void abortContributionTimeout(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, ABORTED_ON_CONTRIBUTION_TIMEOUT);
    }

    @Async
    public void uploadResult(String chainTaskId) {
        customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADING);
        // System.exit(3);

        Eip712Challenge eip712Challenge = resultRepoClient.getChallenge(publicConfigurationService.getChainId());
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();
        String authorizationToken = Eip712ChallengeUtils.buildAuthorizationToken(eip712Challenge,
                workerWalletAddress, ecKeyPair);

        ResponseEntity<String> responseEntity = resultRepoClient.uploadResult(authorizationToken,
                resultService.getResultModelWithZip(chainTaskId));

        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            customFeignClient.updateReplicateStatus(chainTaskId, RESULT_UPLOADED);
        }
    }

    public void completeTask(String chainTaskId) {
        resultService.removeResult(chainTaskId);
        customFeignClient.updateReplicateStatus(chainTaskId, COMPLETED);
    }
}
