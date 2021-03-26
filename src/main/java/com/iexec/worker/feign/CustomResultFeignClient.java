/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    private final ResultClient resultClient;

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

    public String login(Integer chainId, String signedEip712Challenge) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("chainId", chainId);
        arguments.put("signedEip712Challenge", signedEip712Challenge);
        HttpCall<String> httpCall = (args) -> resultClient.login((Integer) args.get("chainId"), (String) args.get("signedEip712Challenge"));
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "login to result proxy");
        return is2xxSuccess(response) ? response.getBody() : "";
    }

    public String uploadResult(String authorizationToken, ResultModel resultModel) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("authorizationToken", authorizationToken);
        arguments.put("resultModel", resultModel);

        HttpCall<String> httpCall = (args) ->
                resultClient.uploadResult((String) args.get("authorizationToken"), (ResultModel) args.get("resultModel"));

        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "uploadResult");
        return is2xxSuccess(response) ? response.getBody() : "";
    }

    public String getIpfsHashForTask(String chainTaskId) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("chainTaskId", chainTaskId);

        HttpCall<String> httpCall = (args) ->
                resultClient.getIpfsHashForTask((String) args.get("chainTaskId"));

        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getIpfsHashForTask");
        return is2xxSuccess(response) ? response.getBody() : "";
    }
}
