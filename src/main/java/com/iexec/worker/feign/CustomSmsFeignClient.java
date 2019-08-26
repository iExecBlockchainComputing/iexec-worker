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
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.scone.SconeSecureSessionResponse;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.client.CoreClient;
import com.iexec.worker.feign.client.ResultClient;
import com.iexec.worker.feign.client.SmsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
public class CustomSmsFeignClient extends BaseFeignClient {

    private LoginService loginService;
    private CoreClient coreClient;
    private SmsClient smsClient;
    private ResultClient resultClient;

    public CustomSmsFeignClient(LoginService loginService,
                             CoreClient coreClient,
                             SmsClient smsClient,
                             ResultClient resultClient) {
        this.loginService = loginService;
        this.coreClient = coreClient;
        this.smsClient = smsClient;
        this.resultClient = resultClient;
    }

    @Override
    boolean login() {
        return loginService.login();
    }

    /*
     * How does it work?
     * We create an HttpCall<T>, T being the type of the response
     * body and it can be Void. We send it along with the arguments
     * to the generic "makeHttpCall()" method. If the call was
     * successful, we return a ResponseEntity<T> with the response
     * body, otherwise, we return a ResponseEntity with call's failure
     * status.
     * 
     * How to pass call args?
     * We put method arguments in an array of objects Object[] (or
     * empty array), we pass the array as an argument
     * to the lambda expression. Inside the lambda expression we 
     * cast the arguments into their original types required by the
     * method to be called (this is safe because we already know
     * the arguments' types).
     */

    // core

    public PublicConfiguration getPublicConfiguration() {
        Object[] arguments = new Object[0];
        HttpCall<PublicConfiguration> httpCall = (args) -> coreClient.getPublicConfiguration();
        ResponseEntity<PublicConfiguration> response = makeHttpCall(httpCall, arguments, "getPublicConfig");
        return isOk(response) ? response.getBody() : null;
    }

    public String getCoreVersion() {
        Object[] arguments = new Object[0];
        HttpCall<String> httpCall = (args) -> coreClient.getCoreVersion();
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getCoreVersion");
        return isOk(response) ? response.getBody() : null;
    }

    public String ping() {
        Object[] arguments = new Object[] {loginService.getToken()};
        HttpCall<String> httpCall = (args) -> coreClient.ping((String) args[0]);
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "ping");
        return isOk(response) && response.getBody() != null ? response.getBody() : "";
    }

    //TODO: Make registerWorker return Worker
    public boolean registerWorker(WorkerModel model) {
        Object[] arguments = new Object[] {loginService.getToken(), model};
        HttpCall<Void> httpCall = (args) -> coreClient.registerWorker((String) args[0], (WorkerModel) args[1]);
        ResponseEntity<Void> response = makeHttpCall(httpCall, arguments, "registerWorker");
        return isOk(response);
    }

    public List<TaskNotification> getMissedTaskNotifications(long lastAvailableBlockNumber) {
        Object[] arguments = new Object[] {loginService.getToken(), lastAvailableBlockNumber};

        HttpCall<List<TaskNotification>> httpCall = (args) ->
                coreClient.getMissedTaskNotifications((String) args[0], (long) args[1]);

        ResponseEntity<List<TaskNotification>> response = makeHttpCall(httpCall, arguments, "getMissedNotifications");
        return isOk(response) ? response.getBody() : Collections.emptyList();
    }

    public Optional<ContributionAuthorization> getAvailableReplicate(long lastAvailableBlockNumber) {
        Object[] arguments = new Object[] {loginService.getToken(), lastAvailableBlockNumber};

        HttpCall<ContributionAuthorization> httpCall = (args) ->
                coreClient.getAvailableReplicate((String) args[0], (long) args[1]);

        ResponseEntity<ContributionAuthorization> response = makeHttpCall(httpCall, arguments, "getAvailableReplicate");
        if (!isOk(response) || response.getBody() == null) {
            return Optional.empty();
        }

        return Optional.of(response.getBody());
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

        Object[] arguments = new Object[] {loginService.getToken(), chainTaskId, status, details};

        HttpCall<TaskNotificationType> httpCall = (args) ->
                coreClient.updateReplicateStatus((String) args[0], (String) args[1],
                        (ReplicateStatus) args[2], (ReplicateDetails) args[3]);

        ResponseEntity<TaskNotificationType> response = makeHttpCall(httpCall, arguments, "updateReplicateStatus");
        if (!isOk(response)) {
            return null;
        }

        log.info(status.toString() + " [chainTaskId:{}]", chainTaskId);
        return response.getBody();
    }

    // sms

    public SmsSecretResponse getTaskSecretsFromSms(SmsRequest smsRequest) {
        Object[] arguments = new Object[] {smsRequest};
        HttpCall<SmsSecretResponse> httpCall = (args) -> smsClient.getTaskSecretsFromSms((SmsRequest) args[0]);
        ResponseEntity<SmsSecretResponse> response = makeHttpCall(httpCall, arguments, "getTaskSecretsFromSms");
        return isOk(response) ? response.getBody() : null;
    }

    public SconeSecureSessionResponse generateSecureSession(SmsRequest smsRequest) {
        Object[] arguments = new Object[] {smsRequest};
        HttpCall<SconeSecureSessionResponse> httpCall = (args) -> smsClient.generateSecureSession((SmsRequest) args[0]);
        ResponseEntity<SconeSecureSessionResponse> response = 
                makeHttpCall(httpCall, arguments, "generateSecureSession");
        return isOk(response) ? response.getBody() : null;
    }

    // result

    public Optional<Eip712Challenge> getResultChallenge(Integer chainId) {
        Object[] arguments = new Object[] {chainId};
        HttpCall<Eip712Challenge> httpCall = (args) -> resultClient.getChallenge((Integer) args[0]);
        ResponseEntity<Eip712Challenge> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return isOk(response) ? Optional.of(response.getBody()) : Optional.empty();
    }

    public String uploadResult(String authorizationToken, ResultModel resultModel) {
        Object[] arguments = new Object[] {authorizationToken, resultModel};

        HttpCall<String> httpCall = (args) ->
                resultClient.uploadResult((String) args[0], (ResultModel) args[0]);

        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return isOk(response) ? response.getBody() : "";
    }
}
