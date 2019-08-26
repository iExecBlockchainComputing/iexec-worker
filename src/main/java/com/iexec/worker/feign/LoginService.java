package com.iexec.worker.feign;

import com.iexec.common.security.Signature;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class LoginService extends BaseFeignClient {

    private static final String TOKEN_PREFIX = "Bearer ";
    private String jwtToken;

    private CredentialsService credentialsService;
    private CoreClient coreClient;

    LoginService(CredentialsService credentialsService, CoreClient coreClient) {
        this.credentialsService = credentialsService;
        this.coreClient = coreClient;
        login();
    }

    public String getToken() {
        return jwtToken;
    }

    @Override
    public boolean login() {
        expireToken();
        String workerAddress = credentialsService.getCredentials().getAddress();
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();

        String challenge = getLoginChallenge(workerAddress);
        if (challenge.isEmpty()) {
            log.error("Cannot login since challenge is empty [challenge:{}]", challenge);
            return false;
        }

        Signature signature = SignatureUtils.hashAndSign(challenge, workerAddress, ecKeyPair);
        String token = requestLogin(workerAddress, signature);
        if (token.isEmpty()) {
            log.error("Cannot login since token is empty [token:{}]", token);
            return false;
        }

        jwtToken = TOKEN_PREFIX + token;
        return true;
    }

    private void expireToken() {
        jwtToken = "";
    }

    private String getLoginChallenge(String workerAddress) {
        Object[] arguments = new Object[] {workerAddress};
        HttpCall<String> httpCall = (args) -> coreClient.getChallenge((String) args[0]);
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "getLoginChallenge");
        return isOk(response) && response.getBody() != null ? response.getBody() : "";
    }

    private String requestLogin(String workerAddress, Signature signature) {
        Object[] arguments = new Object[] {workerAddress, signature};
        HttpCall<String> httpCall = (args) -> coreClient.login((String) args[0], (Signature) args[1]);
        ResponseEntity<String> response = makeHttpCall(httpCall, arguments, "requestLogin");
        return isOk(response) && response.getBody() != null ? response.getBody() : "";
    }
}