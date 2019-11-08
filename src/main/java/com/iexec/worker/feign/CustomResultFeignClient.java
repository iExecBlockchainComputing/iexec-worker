package com.iexec.worker.feign;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.worker.feign.client.ResultClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Service
public class CustomResultFeignClient extends BaseFeignClient {

    private ResultClient resultClient;

    public CustomResultFeignClient(ResultClient resultClient) {
        this.resultClient = resultClient;
    }

    @Override
    String login() {
        return "";
    }

    /*
     * Please refer to the comment in CustomCoreFeignClient.java
     * to understand the usage of the generic makeHttpCall() method.
     */

    public Optional<Eip712Challenge> getResultChallenge(Integer chainId) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("chainId", chainId);
        HttpCall<Eip712Challenge> httpCall = (args) -> resultClient.getChallenge((Integer) args.get("chainId"));
        ResponseEntity<Eip712Challenge> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return is2xxSuccess(response) ? Optional.of(response.getBody()) : Optional.empty();
    }

    public String uploadResult(String authorizationToken, ResultModel resultModel) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("authorizationToken", authorizationToken);
        arguments.put("resultModel", resultModel);

        HttpCall<String> httpCall = (args) ->
                resultClient.uploadResult((String) args.get("authorizationToken"), (ResultModel) args.get("resultModel"));

        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getResultChallenge");
        return is2xxSuccess(response) ? response.getBody() : "";
    }
}
