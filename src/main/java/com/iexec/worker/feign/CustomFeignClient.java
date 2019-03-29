package com.iexec.worker.feign;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.CoreConfigurationService;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CustomFeignClient {


    private static final int RETRY_TIME = 5000;
    private static final String TOKEN_PREFIX = "Bearer ";
    private final String coreURL;

    private CoreClient coreClient;
    private WorkerClient workerClient;
    private ReplicateClient replicateClient;
    private ResultRepoClient resultRepoClient;
    private CredentialsService credentialsService;
    private String currentToken;

    public CustomFeignClient(CoreClient coreClient,
                             WorkerClient workerClient,
                             ReplicateClient replicateClient,
                             ResultRepoClient resultRepoClient,
                             CredentialsService credentialsService,
                             CoreConfigurationService coreConfigurationService) {
        this.coreClient = coreClient;
        this.workerClient = workerClient;
        this.replicateClient = replicateClient;
        this.resultRepoClient = resultRepoClient;
        this.credentialsService = credentialsService;
        this.coreURL = coreConfigurationService.getUrl();
        this.currentToken = "";
    }

    public PublicConfiguration getPublicConfiguration() {
        try {
            return workerClient.getPublicConfiguration();
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getPublicConfiguration, will retry");
                sleep();
                return getPublicConfiguration();
            }
        }
        return null;
    }

    public String getCoreVersion() {
        try {
            return coreClient.getCoreVersion();
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getCoreVersion, will retry");
                sleep();
                return getCoreVersion();
            }
        }
        return null;
    }

    public String ping() {
        try {
            return workerClient.ping(getToken());
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to ping [instance:{}]", coreURL);
            } else if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                return workerClient.ping(getToken());
            }
        }

        return "";
    }

    public void registerWorker(WorkerConfigurationModel model) {
        try {
            workerClient.registerWorker(getToken(), model);
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to registerWorker, will retry [instance:{}]", coreURL);
                sleep();
                registerWorker(model);
            } else if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                workerClient.registerWorker(getToken(), model);
            }
        }
    }

    public List<InterruptedReplicateModel> getInterruptedReplicates(long lastAvailableBlockNumber) {
        try {
            return replicateClient.getInterruptedReplicates(lastAvailableBlockNumber, getToken());
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getInterruptedReplicates, will retry [instance:{}]", coreURL);
                sleep();
                return getInterruptedReplicates(lastAvailableBlockNumber);
            } else if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                return getInterruptedReplicates(lastAvailableBlockNumber);
            }
        }
        return null;
	}

    public List<String> getTasksInProgress(){
        try {
            return workerClient.getCurrentTasks(getToken());
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to get tasks in progress, will retry [instance:{}]", coreURL);
                sleep();
            } else if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                return workerClient.getCurrentTasks(getToken());
            }
        }

        return Collections.emptyList();
    }

    public Optional<ContributionAuthorization> getAvailableReplicate(long lastAvailableBlockNumber) {
        try {
            return Optional.ofNullable(replicateClient.getAvailableReplicate(lastAvailableBlockNumber, getToken()));
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getAvailableReplicate [instance:{}]", coreURL);
            } else if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                return Optional.of(replicateClient.getAvailableReplicate(lastAvailableBlockNumber, getToken()));
            }
        }
        return Optional.empty();
    }

    // TODO: those next 4 methods need to be refactored
    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status) {
        updateReplicateStatus(chainTaskId, status, null, "");
    }

    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status, String resultLink) {
        updateReplicateStatus(chainTaskId, status, null, resultLink);
    }

    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status, ChainReceipt chainReceipt) {
        updateReplicateStatus(chainTaskId, status, chainReceipt, "");
    }

    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status, ChainReceipt chainReceipt, String resultLink) {
        log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);

        ReplicateDetails details = ReplicateDetails.builder()
                .chainReceipt(chainReceipt)
                .resultLink(resultLink)
                .build();

        try {
            replicateClient.updateReplicateStatus(chainTaskId, status, getToken(), details);
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to updateReplicateStatus, will retry [instance:{}]", coreURL);
                sleep();
                updateReplicateStatus(chainTaskId, status, chainReceipt, resultLink);
                return;
            }

            if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                generateNewToken();
                log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);
                replicateClient.updateReplicateStatus(chainTaskId, status, getToken(), details);
            }
        }
    }

    private String getCoreChallenge(String workerAddress) {
        try {
            return workerClient.getChallenge(workerAddress);
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getCoreChallenge, will retry [instance:{}]", coreURL);
                sleep();
                return getCoreChallenge(workerAddress);
            }
        }
        return null;
    }

    public Optional<Eip712Challenge> getResultRepoChallenge(Integer chainId) {
        try {
            return Optional.of(resultRepoClient.getChallenge(chainId));
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to getResultRepoChallenge, will retry");
                sleep();
                return getResultRepoChallenge(chainId);
            }
        }
        return Optional.empty();
    }

    public boolean uploadResult(String authorizationToken, ResultModel resultModel) {
        try {
            return resultRepoClient.uploadResult(authorizationToken, resultModel)
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (FeignException e) {
            log.error("Failed to uploadResult [instance:{}]");
            return false;
        }
    }

    private String login(String workerAddress, Signature signature) {
        try {
            return workerClient.login(workerAddress, signature);
        } catch (FeignException e) {
            if (e.status() == 0) {
                log.error("Failed to login, will retry [instance:{}]", coreURL);
                sleep();
                return login(workerAddress, signature);
            }
        }
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_TIME);
        } catch (InterruptedException e) {
        }
    }

    private String getToken() {
        if (currentToken.isEmpty()) {
            String workerAddress = credentialsService.getCredentials().getAddress();
            ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();
            String challenge = getCoreChallenge(workerAddress);

            Signature signature = SignatureUtils.hashAndSign(challenge, workerAddress, ecKeyPair);
            currentToken = TOKEN_PREFIX + login(workerAddress, signature);
        }

        return currentToken;
    }

    private void expireToken() {
        currentToken = "";
    }

    private String generateNewToken() {
        expireToken();
        return getToken();
    }

}
