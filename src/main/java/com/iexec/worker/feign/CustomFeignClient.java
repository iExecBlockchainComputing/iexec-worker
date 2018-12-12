package com.iexec.worker.feign;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.security.TokenService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CustomFeignClient {


    private TokenService tokenService;
    private CoreWorkerClient coreWorkerClient;
    private CoreTaskClient coreTaskClient;

    public CustomFeignClient(
            TokenService tokenService,
            CoreWorkerClient coreWorkerClient,
            CoreTaskClient coreTaskClient) {
        this.tokenService = tokenService;
        this.coreWorkerClient = coreWorkerClient;
        this.coreTaskClient = coreTaskClient;
    }


    public void ping() {
        try {
            coreWorkerClient.ping(tokenService.getToken());
        } catch (FeignException e) {
            if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                tokenService.generateNewToken();
                coreWorkerClient.ping(tokenService.getToken());
            }
        }
    }

    public void registerWorker(WorkerConfigurationModel model) {
        try {
            coreWorkerClient.registerWorker(tokenService.getToken(), model);
        } catch (FeignException e) {
            if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                tokenService.generateNewToken();
                coreWorkerClient.registerWorker(tokenService.getToken(), model);
            }
        }
    }

    public ContributionAuthorization getAvailableReplicate(String workerEnclaveAdress) {
        try {
            return coreTaskClient.getAvailableReplicate(
                    tokenService.getToken(),
                    workerEnclaveAdress);
        } catch (FeignException e) {
            if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                tokenService.generateNewToken();
                return coreTaskClient.getAvailableReplicate(
                        tokenService.getToken(),
                        workerEnclaveAdress);
            }
        }
        return null;
    }

    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status) {
        try {
            coreTaskClient.updateReplicateStatus(chainTaskId, status, tokenService.getToken());
        } catch (FeignException e) {
            tokenService.generateNewToken();
            if (HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED)) {
                tokenService.generateNewToken();
                coreTaskClient.updateReplicateStatus(chainTaskId, status, tokenService.getToken());
            }
        }
    }
}
