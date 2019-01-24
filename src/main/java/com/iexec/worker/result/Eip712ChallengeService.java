package com.iexec.worker.result;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.security.SignatureService;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Eip712ChallengeService {

    private SignatureService signatureService;
    private WorkerConfigurationService workerConfigurationService;

    Eip712ChallengeService(SignatureService signatureService,
                           WorkerConfigurationService workerConfigurationService) {
        this.signatureService = signatureService;
        this.workerConfigurationService = workerConfigurationService;
    }

    public String buildAuthorizationToken(Eip712Challenge eip712Challenge) {
        String walletAddress = workerConfigurationService.getWorkerWalletAddress();

        String challengeString = Eip712ChallengeUtils.getEip712ChallengeString(eip712Challenge);
        String signatureString = signatureService.signAsString(challengeString);

        String authoriaztionToken = String.join("_", challengeString, signatureString, walletAddress);

        return authoriaztionToken;
    }
}
