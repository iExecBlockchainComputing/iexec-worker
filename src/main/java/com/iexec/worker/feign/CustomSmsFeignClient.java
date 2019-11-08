package com.iexec.worker.feign;

import java.util.HashMap;
import java.util.Map;

import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.scone.SconeSecureSessionResponse;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.client.SmsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


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

    public SmsSecretResponse getTaskSecretsFromSms(SmsRequest smsRequest) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("smsRequest", smsRequest);
        HttpCall<SmsSecretResponse> httpCall = (args) -> smsClient.getTaskSecretsFromSms((SmsRequest) args.get("smsRequest"));
        ResponseEntity<SmsSecretResponse> response = makeHttpCall(httpCall, arguments, "getTaskSecretsFromSms");
        return is2xxSuccess(response) ? response.getBody() : null;
    }

    public SconeSecureSessionResponse generateSecureSession(SmsRequest smsRequest) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("smsRequest", smsRequest);
        HttpCall<SconeSecureSessionResponse> httpCall = (args) -> smsClient.generateSecureSession((SmsRequest) args.get("smsRequest"));
        ResponseEntity<SconeSecureSessionResponse> response = 
                makeHttpCall(httpCall, arguments, "generateSecureSession");
        return is2xxSuccess(response) ? response.getBody() : null;
    }
}
