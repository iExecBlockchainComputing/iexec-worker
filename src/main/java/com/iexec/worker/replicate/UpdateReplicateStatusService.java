package com.iexec.worker.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.worker.feign.CoreTaskClient;
import com.iexec.worker.security.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UpdateReplicateStatusService {

    private TokenService tokenService;
    private CoreTaskClient coreTaskClient;

    public UpdateReplicateStatusService(TokenService tokenService,
                                        CoreTaskClient coreTaskClient) {
        this.tokenService = tokenService;
        this.coreTaskClient = coreTaskClient;
    }

    public void updateReplicateStatus(String chainTaskId, ReplicateStatus status) {
        String token = tokenService.getToken();
        log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);
        try {
            coreTaskClient.updateReplicateStatus(chainTaskId, status, token);
        } catch (Exception e) {
            log.warn("Token doesn't seem valid anymore, asking a new one");
            // if an exception is thrown, another token should be asked
            token = tokenService.generateNewToken();
            try {
                coreTaskClient.updateReplicateStatus(chainTaskId, status, token);
            } catch (Exception e1) {
                log.warn("Error in the call to updateReplicateStatus.");

            }
        }
    }
}
