package com.iexec.worker.feign;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ResultModel;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.security.TokenService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


@Slf4j
@Service
public class CustomFeignClient extends BaseFeignClient {

    private static final int RETRY_TIME = 5000;
    private final String coreURL;
    private TokenService tokenService;
    private CoreClient coreClient;

    public CustomFeignClient(CoreClient coreClient,
                             CoreConfigurationService coreConfigurationService,
                             TokenService tokenService) {
        this.coreClient = coreClient;
        this.tokenService = tokenService;
        this.coreURL = coreConfigurationService.getUrl();
    }

    @Override
    void login() {
        
    }

    /*
     * How does it work?
     * We create a consumer/supplier/function with the method to
     * be called. We send it along with the arguments to the
     * 
     * 
     * Types of calls:
     *      - consumer for calls with one arg and no return value.
     *      - supplier for calls with no args and a return value.
     *      - function for calls with one arg and a return value.
     * 
     * How to pass args?
     * We put method arguments in an array of objects Object[] (or
     * empty array), we pass the array as an argument
     * to the lambda expression. Inside the lambda expression we 
     * cast the arguments into their original types required by the
     * method to be called (this is almost safe because we already
     * know the arguments' types).
     */

     public boolean ping() {
        Object[] arguments = new Object[] {tokenService.getToken()};
        HttpCall<Void> httpCall = (args) -> coreClient.ping((String) args[0]);
        ResponseEntity<Void> response = makeHttpCall(httpCall, arguments, "ping");
        return response != null ? isOk(response.getStatusCodeValue()) : false;
    }

    public PublicConfiguration getPublicConfiguration() {
        Object[] arguments = new Object[0];
        HttpCall<PublicConfiguration> httpCall = (args) -> coreClient.getPublicConfiguration();
        ResponseEntity<PublicConfiguration> response = makeHttpCall(httpCall, arguments, "getPublicConfig");
        return response != null ? response.getBody() : null;
    }

    public String getCoreVersion() {
        Object[] arguments = new Object[0];
        HttpCall<String> httpCall = (args) -> coreClient.getCoreVersion();
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getCoreVersion");
        return response != null ? response.getBody() : null;
    }

    //TODO: Make registerWorker return Worker
    public boolean registerWorker(WorkerModel model) {
        Object[] arguments = new Object[] {tokenService.getToken(), model};
        HttpCall<Void> httpCall = (args) -> coreClient.registerWorker((String) args[0], (WorkerModel) model);
        ResponseEntity<Void> response = makeHttpCall(httpCall, arguments, "registerWorker");
        return response != null ? isOk(response.getStatusCodeValue()) : false;
    }

    public List<TaskNotification> getMissedTaskNotifications(long lastAvailableBlockNumber) {
        try {
            return coreClient.getMissedTaskNotifications(lastAvailableBlockNumber, tokenService.getToken());
        } catch (FeignException e) {
            if (isUnauthorized(e)) {
                tokenService.expireToken();
            } else {
                log.error("Failed to getMissedTaskNotifications (will retry) [instance:{}, status:{}]", coreURL, e.status());
            }
            sleep();
            return getMissedTaskNotifications(lastAvailableBlockNumber);
        }
    }

    public Optional<ContributionAuthorization> getAvailableReplicate(long lastAvailableBlockNumber) {
        try {
            ContributionAuthorization contributionAuth = coreClient.getAvailableReplicate(lastAvailableBlockNumber, tokenService.getToken());
            return contributionAuth == null ? Optional.empty() : Optional.of(contributionAuth);
        } catch (FeignException e) {
            if (isUnauthorized(e)) {
                tokenService.expireToken();
            } else {
                log.error("Failed to getAvailableReplicate (will retry) [instance:{}, status:{}]", coreURL, e.status());
            }
            sleep();
            return getAvailableReplicate(lastAvailableBlockNumber);
        }
    }

    public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status) {
        return updateReplicateStatus(chainTaskId, status, ReplicateDetails.builder().build());
    }

    public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status,
                                                      ReplicateStatusCause cause) {
        ReplicateDetails replicateDetails = ReplicateDetails.builder().replicateStatusCause(cause).build();
        return updateReplicateStatus(chainTaskId, status, replicateDetails);
    }

    public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status,
                                                      ReplicateDetails details) {
        try {
            TaskNotificationType taskNotificationType = coreClient.updateReplicateStatus(chainTaskId, status, tokenService.getToken(), details);
            log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);
            return taskNotificationType;
        } catch (FeignException e) {
            if (isUnauthorized(e)) {
                tokenService.expireToken();
            } else {
                log.error("Failed to updateReplicateStatus (will retry) [instance:{}, status:{}]", coreURL, e.status());
            }
            sleep();
            return updateReplicateStatus(chainTaskId, status, details);
        }
    }

    // public String ping() {
    //     try {
    //         return coreClient.ping(tokenService.getToken());
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to ping core, retrying... [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return ping();
    //     }
    // }

    // public PublicConfiguration getPublicConfiguration() {
    //     try {
    //         return coreClient.getPublicConfiguration();
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to getPublicConfiguration (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return getPublicConfiguration();
    //     }
    // }

    // public String getCoreVersion() {
    //     try {
    //         return coreClient.getCoreVersion();
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to getCoreVersion (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return getCoreVersion();
    //     }
    // }

    // //TODO: Make registerWorker return Worker
    // public void registerWorker(WorkerModel model) {
    //     try {
    //         coreClient.registerWorker(tokenService.getToken(), model);
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to registerWorker (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         registerWorker(model);
    //     }
    // }

    // public List<TaskNotification> getMissedTaskNotifications(long lastAvailableBlockNumber) {
    //     try {
    //         return coreClient.getMissedTaskNotifications(lastAvailableBlockNumber, tokenService.getToken());
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to getMissedTaskNotifications (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return getMissedTaskNotifications(lastAvailableBlockNumber);
    //     }
    // }

    // public Optional<ContributionAuthorization> getAvailableReplicate(long lastAvailableBlockNumber) {
    //     try {
    //         ContributionAuthorization contributionAuth = coreClient.getAvailableReplicate(lastAvailableBlockNumber, tokenService.getToken());
    //         return contributionAuth == null ? Optional.empty() : Optional.of(contributionAuth);
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to getAvailableReplicate (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return getAvailableReplicate(lastAvailableBlockNumber);
    //     }
    // }

    // public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status) {
    //     return updateReplicateStatus(chainTaskId, status, ReplicateDetails.builder().build());
    // }

    // public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status,
    //                                                   ReplicateStatusCause cause) {
    //     ReplicateDetails replicateDetails = ReplicateDetails.builder().replicateStatusCause(cause).build();
    //     return updateReplicateStatus(chainTaskId, status, replicateDetails);
    // }

    // public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatus status,
    //                                                   ReplicateDetails details) {
    //     try {
    //         TaskNotificationType taskNotificationType = coreClient.updateReplicateStatus(chainTaskId, status, tokenService.getToken(), details);
    //         log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);
    //         return taskNotificationType;
    //     } catch (FeignException e) {
    //         if (isUnauthorized(e)) {
    //             tokenService.expireToken();
    //         } else {
    //             log.error("Failed to updateReplicateStatus (will retry) [instance:{}, status:{}]", coreURL, e.status());
    //         }
    //         sleep();
    //         return updateReplicateStatus(chainTaskId, status, details);
    //     }
    // }

    // private boolean isUnauthorized(FeignException e) {
    //     return e != null && e.status() > 0 && HttpStatus.valueOf(e.status()).equals(HttpStatus.UNAUTHORIZED);
    // }

    private void sleep() {
        try {
            Thread.sleep(RETRY_TIME);
        } catch (InterruptedException e) {
        }
    }

}
