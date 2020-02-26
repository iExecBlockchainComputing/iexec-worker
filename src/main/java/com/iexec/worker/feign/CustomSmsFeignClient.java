package com.iexec.worker.feign;

import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.client.SmsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class CustomSmsFeignClient extends BaseFeignClient {

    private SmsClient smsClient;

    public CustomSmsFeignClient(SmsClient smsClient) {
        this.smsClient = smsClient;
    }

    @Override
    String login() {
        return "";
    }

    /*
     * Please refer to the comment in CustomCoreFeignClient.java
     * to understand the usage of the generic makeHttpCall() method.
     */

    public SmsSecretResponse getUnTeeSecrets(SmsRequest smsRequest) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("smsRequest", smsRequest);
        HttpCall<SmsSecretResponse> httpCall = (args) -> smsClient.getUnTeeSecrets((SmsRequest) args.get("smsRequest"));
        ResponseEntity<SmsSecretResponse> response = makeHttpCall(httpCall, arguments, "getUnTeeSecrets");
        return isOk(response) ? response.getBody() : null;
    }

    public String generateTeeSession(SmsRequest smsRequest) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("smsRequest", smsRequest);
        HttpCall<String> httpCall = (args) -> smsClient.generateTeeSession((SmsRequest) args.get("smsRequest"));
        ResponseEntity<String> response =
                makeHttpCall(httpCall, arguments, "generateTeeSession");
        return isOk(response) ? response.getBody() : null;
    }
}
