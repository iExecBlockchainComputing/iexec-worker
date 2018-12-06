package com.iexec.worker.security;

import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.CoreWorkerClient;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private CoreWorkerClient coreWorkerClient;
    private CredentialsService credentialsService;
    private SignatureService signatureService;

    private String currentToken;

    public TokenService(CoreWorkerClient coreWorkerClient,
                        CredentialsService credentialsService,
                        SignatureService signatureService) {
        this.coreWorkerClient = coreWorkerClient;
        this.credentialsService = credentialsService;
        this.signatureService = signatureService;
        this.currentToken = "";
    }

    public String getToken() {
        if (currentToken.isEmpty()){
            String workerAddress = credentialsService.getCredentials().getAddress();
            String challenge = coreWorkerClient.getChallenge(workerAddress);
            currentToken = "Bearer wrong " + coreWorkerClient.login(workerAddress, signatureService.createSignature(challenge));
        }
        return currentToken;
    }

    public void expireToken(){
        currentToken = "";
    }
}
