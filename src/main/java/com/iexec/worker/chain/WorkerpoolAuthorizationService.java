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

package com.iexec.worker.chain;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.common.utils.purge.ExpiringTaskMapFactory;
import com.iexec.common.utils.purge.Purgeable;
import com.iexec.worker.config.PublicConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;


@Slf4j
@Service
public class WorkerpoolAuthorizationService implements Purgeable {

    private final PublicConfigurationService publicConfigurationService;
    private final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations;
    private String corePublicAddress;

    public WorkerpoolAuthorizationService(PublicConfigurationService publicConfigurationService) {
        this.publicConfigurationService = publicConfigurationService;
        workerpoolAuthorizations = ExpiringTaskMapFactory.getExpiringTaskMap();
    }

    @PostConstruct
    public void initIt() {
        corePublicAddress = publicConfigurationService.getSchedulerPublicAddress();
    }


    public boolean isWorkerpoolAuthorizationValid(WorkerpoolAuthorization auth, String signerAddress) {
        // create the hash that was used in the signature in the core
        byte[] message = BytesUtils.stringToBytes(
                HashUtils.concatenateAndHash(auth.getWorkerWallet(), auth.getChainTaskId(), auth.getEnclaveChallenge()));

        return SignatureUtils.isSignatureValid(message, auth.getSignature(), signerAddress);
    }

    public boolean putWorkerpoolAuthorization(WorkerpoolAuthorization workerpoolAuthorization) {
        if (workerpoolAuthorization == null || workerpoolAuthorization.getChainTaskId() == null) {
            log.error("Cant putWorkerpoolAuthorization (null) [workerpoolAuthorization:{}]", workerpoolAuthorization);
            return false;
        }

        if (!isWorkerpoolAuthorizationValid(workerpoolAuthorization, corePublicAddress)) {
            log.error("Cant putWorkerpoolAuthorization (invalid) [workerpoolAuthorization:{}]", workerpoolAuthorization);
            return false;
        }
        workerpoolAuthorizations.putIfAbsent(workerpoolAuthorization.getChainTaskId(), workerpoolAuthorization);
        return true;
    }

    WorkerpoolAuthorization getWorkerpoolAuthorization(String chainTaskId) {
        return workerpoolAuthorizations.get(chainTaskId);
    }

    @Override
    public boolean purgeTask(String chainTaskId) {
        return workerpoolAuthorizations.remove(chainTaskId) != null;
    }
}
