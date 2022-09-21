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

package com.iexec.worker.sms;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.common.web.ApiResponseBodyDecoder;
import com.iexec.sms.api.*;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
public class SmsService implements Purgeable {

    private final CredentialsService credentialsService;
    private final SmsClientProvider smsClientProvider;
    private final Map<String, String> taskIdToSmsUrl = new HashMap<>();

    public SmsService(CredentialsService credentialsService,
                      SmsClientProvider smsClientProvider,
                      CoreClient coreClient) {
        this.credentialsService = credentialsService;
        this.smsClientProvider = smsClientProvider;
    }

    public void attachSmsUrlToTask(String chainTaskId, String smsUrl) {
        taskIdToSmsUrl.put(chainTaskId, smsUrl);
    }

    public SmsClient getSmsClient(String chainTaskId) {
        String url = taskIdToSmsUrl.get(chainTaskId);
        if(StringUtils.isEmpty(url)){
            // if url is not here anymore, worker might hit core on GET /tasks 
            // to retrieve SMS URL
            throw new SmsClientCreationException("No SMS URL defined for " +
                "given task [chainTaskId: " + chainTaskId +"]");
        }
        return smsClientProvider.getSmsClient(url);
    }

    // TODO: use the below method with retry.
    public TeeSessionGenerationResponse createTeeSession(WorkerpoolAuthorization workerpoolAuthorization) throws TeeSessionGenerationException {
        String chainTaskId = workerpoolAuthorization.getChainTaskId();
        log.info("Creating TEE session [chainTaskId:{}]", chainTaskId);
        String authorization = getAuthorizationString(workerpoolAuthorization);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        SmsClient smsClient = getSmsClient(chainTaskId);

        try {
            TeeSessionGenerationResponse session = smsClient
                    .generateTeeSession(authorization, workerpoolAuthorization)
                    .getData();
            log.info("Created TEE session [chainTaskId:{}, session:{}]",
                    chainTaskId, session);
            return session;
        } catch(FeignException e) {
            log.error("SMS failed to create TEE session [chainTaskId:{}]",
                    chainTaskId, e);
            final Optional<TeeSessionGenerationError> error = ApiResponseBodyDecoder.getErrorFromResponse(e.contentUTF8(), TeeSessionGenerationError.class);
            throw new TeeSessionGenerationException(error.orElse(TeeSessionGenerationError.UNKNOWN_ISSUE));
        }
    }

    private String getAuthorizationString(WorkerpoolAuthorization workerpoolAuthorization) {
        String challenge = workerpoolAuthorization.getHash();
        return credentialsService.hashAndSignMessage(challenge).getValue();
    }

    @Override
    public boolean purgeTask(String chainTaskId) {
        taskIdToSmsUrl.remove(chainTaskId);
        return !taskIdToSmsUrl.containsKey(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        taskIdToSmsUrl.clear();  
    }

}
