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

import com.iexec.common.security.Signature;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LoginService extends BaseFeignClient {

    private static final String TOKEN_PREFIX = "Bearer ";
    private String jwtToken;

    private final CredentialsService credentialsService;
    private final CoreClient coreClient;

    LoginService(CredentialsService credentialsService, CoreClient coreClient) {
        this.credentialsService = credentialsService;
        this.coreClient = coreClient;
    }

    public String getToken() {
        return jwtToken;
    }

    @Override
    public String login() {
        expireToken();

        String workerAddress = credentialsService.getCredentials().getAddress();
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();

        String challenge = getLoginChallenge(workerAddress);
        if (challenge == null || challenge.isEmpty()) {
            log.error("Cannot login since challenge is empty [challenge:{}]", challenge);
            return "";
        }

        Signature signature = SignatureUtils.hashAndSign(challenge, workerAddress, ecKeyPair);
        String token = requestLogin(workerAddress, signature);
        if (token == null || token.isEmpty()) {
            log.error("Cannot login since token is empty [token:{}]", token);
            return "";
        }

        jwtToken = TOKEN_PREFIX + token;
        return jwtToken;
    }

    private void expireToken() {
        jwtToken = "";
    }

    private String getLoginChallenge(String workerAddress) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("workerAddress", workerAddress);
        HttpCall<String> httpCall = args -> coreClient.getChallenge((String) args.get("workerAddress"));
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getLoginChallenge");
        return is2xxSuccess(response) && response.getBody() != null ? response.getBody() : "";
    }

    private String requestLogin(String workerAddress, Signature signature) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("workerAddress", workerAddress);
        arguments.put("signature", signature);
        HttpCall<String> httpCall = args -> coreClient.login((String) args.get("workerAddress"), (Signature) args.get("signature"));
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "requestLogin");
        return is2xxSuccess(response) && response.getBody() != null ? response.getBody() : "";
    }
}