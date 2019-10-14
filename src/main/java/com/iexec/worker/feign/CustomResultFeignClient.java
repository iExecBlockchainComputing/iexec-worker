package com.iexec.worker.feign;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.worker.feign.client.ResultClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class CustomResultFeignClient extends BaseFeignClient {

    private LoginService loginService;
    private ResultClient resultClient;

    public CustomResultFeignClient(ResultClient resultClient, LoginService loginService) {
        this.loginService = loginService;
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

    public Optional<Eip712Challenge> getResultChallenge(Integer chainId) {
        Object[] arguments = new Object[] {chainId};
        HttpCall<Eip712Challenge> httpCall = (args) -> resultClient.getChallenge((Integer) args[0]);
        ResponseEntity<Eip712Challenge> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return isOk(response) ? Optional.of(response.getBody()) : Optional.empty();
    }

    public String uploadResult(String authorizationToken, ResultModel resultModel) {
        Object[] arguments = new Object[] {authorizationToken, resultModel};

        HttpCall<String> httpCall = (args) ->
                resultClient.uploadResult((String) args[0], (ResultModel) args[1]);

        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return isOk(response) ? response.getBody() : "";
    }
}
