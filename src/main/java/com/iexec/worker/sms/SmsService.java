/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.sms;

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.common.web.ApiResponseBodyDecoder;
import com.iexec.commons.poco.chain.SignerService;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.sms.api.*;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;

import static com.iexec.sms.secret.ReservedSecretKeyName.IEXEC_RESULT_IEXEC_IPFS_TOKEN;

@Slf4j
@Service
public class SmsService implements Purgeable {

    private final SignerService signerService;
    private final SmsClientProvider smsClientProvider;
    private final Map<String, String> taskIdToSmsUrl = ExpiringTaskMapFactory.getExpiringTaskMap();

    public SmsService(SignerService signerService,
                      SmsClientProvider smsClientProvider) {
        this.signerService = signerService;
        this.smsClientProvider = smsClientProvider;
    }

    public void attachSmsUrlToTask(final String chainTaskId, final String smsUrl) {
        taskIdToSmsUrl.put(chainTaskId, smsUrl);
    }

    public SmsClient getSmsClient(final String chainTaskId) {
        final String url = taskIdToSmsUrl.get(chainTaskId);
        if (StringUtils.isEmpty(url)) {
            // if url is not here anymore, worker might hit core on GET /tasks 
            // to retrieve SMS URL
            throw new SmsClientCreationException("No SMS URL defined for " +
                    "given task [chainTaskId: " + chainTaskId + "]");
        }
        return smsClientProvider.getSmsClient(url);
    }

    /**
     * Checks if a JWT is present to upload results for TEE tasks.
     *
     * @param workerpoolAuthorization Authorization
     * @return {@literal true} if an entry was found, {@literal false} if the secret was not found or an error happened
     */
    private boolean isTokenPresent(final WorkerpoolAuthorization workerpoolAuthorization) {
        final SmsClient smsClient = getSmsClient(workerpoolAuthorization.getChainTaskId());
        try {
            smsClient.isWeb2SecretSet(workerpoolAuthorization.getWorkerWallet(), IEXEC_RESULT_IEXEC_IPFS_TOKEN);
            return true;
        } catch (FeignException.NotFound e) {
            log.info("Worker Result Proxy JWT does not exist in SMS");
        } catch (FeignException e) {
            log.error("Worker Result Proxy JWT existence check failed with error", e);
        }
        return false;
    }

    /**
     * Push a JWT as a Web2 secret in the SMS.
     *
     * @param workerpoolAuthorization Authorization
     * @param token                   JWT to push in the SMS
     * @return {@literal true} if secret is in SMS, {@literal false} otherwise
     */
    public boolean pushToken(final WorkerpoolAuthorization workerpoolAuthorization, final String token) {
        final SmsClient smsClient = getSmsClient(workerpoolAuthorization.getChainTaskId());
        try {
            final String challenge = HashUtils.concatenateAndHash(
                    Hash.sha3String("IEXEC_SMS_DOMAIN"),
                    workerpoolAuthorization.getWorkerWallet(),
                    Hash.sha3String(IEXEC_RESULT_IEXEC_IPFS_TOKEN),
                    Hash.sha3String(token));
            final String authorization = signerService.signMessageHash(challenge).getValue();
            if (authorization.isEmpty()) {
                log.error("Couldn't sign challenge for an unknown reason [hash:{}]", challenge);
                return false;
            }

            if (isTokenPresent(workerpoolAuthorization)) {
                smsClient.updateWeb2Secret(authorization, workerpoolAuthorization.getWorkerWallet(), IEXEC_RESULT_IEXEC_IPFS_TOKEN, token);
            } else {
                smsClient.setWeb2Secret(authorization, workerpoolAuthorization.getWorkerWallet(), IEXEC_RESULT_IEXEC_IPFS_TOKEN, token);
            }
            smsClient.isWeb2SecretSet(workerpoolAuthorization.getWorkerWallet(), IEXEC_RESULT_IEXEC_IPFS_TOKEN);
            return true;
        } catch (Exception e) {
            log.error("Failed to push Web2 secret to SMS", e);
        }
        return false;
    }

    // TODO: use the below method with retry.
    public TeeSessionGenerationResponse createTeeSession(final WorkerpoolAuthorization workerpoolAuthorization) throws TeeSessionGenerationException {
        final String chainTaskId = workerpoolAuthorization.getChainTaskId();
        log.info("Creating TEE session [chainTaskId:{}]", chainTaskId);
        final String authorization = getAuthorizationString(workerpoolAuthorization);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = getSmsClient(chainTaskId);

        try {
            final TeeSessionGenerationResponse session = smsClient
                    .generateTeeSession(authorization, workerpoolAuthorization)
                    .getData();
            log.info("Created TEE session [chainTaskId:{}, session:{}]",
                    chainTaskId, session);
            return session;
        } catch (FeignException e) {
            log.error("SMS failed to create TEE session [chainTaskId:{}]",
                    chainTaskId, e);
            final Optional<TeeSessionGenerationError> error = ApiResponseBodyDecoder.getErrorFromResponse(e.contentUTF8(), TeeSessionGenerationError.class);
            throw new TeeSessionGenerationException(error.orElse(TeeSessionGenerationError.UNKNOWN_ISSUE));
        }
    }

    private String getAuthorizationString(final WorkerpoolAuthorization workerpoolAuthorization) {
        final String challenge = workerpoolAuthorization.getHash();
        return signerService.signMessageHash(challenge).getValue();
    }

    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        taskIdToSmsUrl.remove(chainTaskId);
        return !taskIdToSmsUrl.containsKey(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        taskIdToSmsUrl.clear();
    }

}
