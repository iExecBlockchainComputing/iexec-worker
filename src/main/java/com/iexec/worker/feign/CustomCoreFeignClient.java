package com.iexec.worker.feign;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.worker.feign.client.CoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
public class CustomCoreFeignClient extends BaseFeignClient {

    private LoginService loginService;
    private CoreClient coreClient;

    public CustomCoreFeignClient(CoreClient coreClient, LoginService loginService) {
        this.loginService = loginService;
        this.coreClient = coreClient;
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
        HttpCall<PublicConfiguration> httpCall = (args) -> coreClient.getPublicConfiguration();
        ResponseEntity<PublicConfiguration> response = makeHttpCall(httpCall, null, "getPublicConfig");
        return isOk(response) ? response.getBody() : null;
    }

    public String getCoreVersion() {
        HttpCall<String> httpCall = (args) -> coreClient.getCoreVersion();
        ResponseEntity<String> response = makeHttpCall(httpCall, null, "getCoreVersion");
        return isOk(response) ? response.getBody() : null;
    }

    public String ping() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("token", loginService.getToken());
        HttpCall<String> httpCall = (args) -> coreClient.ping((String) args.get("token"));
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

    public TaskNotificationType updateReplicateStatus(String chainTaskId, ReplicateStatusUpdate replicateStatusUpdate) {

        Object[] arguments = new Object[] {loginService.getToken(), chainTaskId, replicateStatusUpdate};

        HttpCall<TaskNotificationType> httpCall = (args) ->
                coreClient.updateReplicateStatus((String) args[0], (String) args[1], (ReplicateStatusUpdate) args[2]);

        ResponseEntity<TaskNotificationType> response = makeHttpCall(httpCall, arguments, "updateReplicateStatus");
        if (!isOk(response)) {
            return null;
        }

        log.info(replicateStatusUpdate.getStatus().toString() + " [chainTaskId:{}]", chainTaskId);
        return response.getBody();
    }
}
