package com.iexec.worker.feign;

import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.scone.SconeSecureSessionResponse;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.worker.feign.client.SmsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


@Service
public class CustomSmsFeignClient extends BaseFeignClient {

    private LoginService loginService;
    private SmsClient smsClient;

    public CustomSmsFeignClient(SmsClient smsClient, LoginService loginService) {
        this.loginService = loginService;
        this.smsClient = smsClient;
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
}
