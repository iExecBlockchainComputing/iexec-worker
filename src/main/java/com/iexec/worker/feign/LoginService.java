/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import java.util.Objects;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class LoginService {

    static final String TOKEN_PREFIX = "Bearer ";
    private String jwtToken;

    private final CredentialsService credentialsService;
    private final CoreClient coreClient;
    private final Semaphore lock = new Semaphore(1);

    LoginService(CredentialsService credentialsService, CoreClient coreClient) {
        this.credentialsService = credentialsService;
        this.coreClient = coreClient;
    }

    public String getToken() {
        return jwtToken;
    }

    /**
     * Log in the Scheduler.
     * <p>
     * Thread safety is implemented with a {@link Semaphore} and a {@code try {} finally {}} block.
     * The lock is acquired before entering the {@code try} block.
     * The latter has been added to ensure the lock will always be released once acquired.
     * If the lock is not acquired, a login procedure is already ongoing and the method returns immediately.
     *
     * @return An authentication token
     */
    public String login() {
        if (!lock.tryAcquire()) {
            log.info("login already ongoing");
            return "";
        }
        try {
            final String oldToken = jwtToken;
            expireToken();

            String workerAddress = credentialsService.getCredentials().getAddress();
            ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();

            final String challenge;
            try {
                challenge = coreClient.getChallenge(workerAddress);
            } catch (FeignException e) {
                log.error("Cannot login, failed to get challenge [status:{}]", e.status());
                return "";
            }
            if (StringUtils.isEmpty(challenge)) {
                log.error("Cannot login, challenge is empty [challenge:{}]", challenge);
                return "";
            }

            Signature signature = SignatureUtils.hashAndSign(challenge, workerAddress, ecKeyPair);
            final String token;
            try {
                token = coreClient.login(workerAddress, signature);
            } catch (FeignException e) {
                log.error("Cannot login, failed to get token [status:{}]", e.status());
                return "";
            }
            if (StringUtils.isEmpty(token)) {
                log.error("Cannot login, token is empty [token:{}]", token);
                return "";
            }

            jwtToken = TOKEN_PREFIX + token;
            log.info("Retrieved {} JWT token from scheduler", Objects.equals(oldToken, jwtToken) ? "existing" : "new");
            return jwtToken;
        } finally {
            lock.release();
        }
    }

    private void expireToken() {
        jwtToken = "";
    }
}
