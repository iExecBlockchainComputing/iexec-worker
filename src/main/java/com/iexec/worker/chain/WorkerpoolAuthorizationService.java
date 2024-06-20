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

package com.iexec.worker.chain;

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.worker.config.SchedulerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;


@Slf4j
@Service
public class WorkerpoolAuthorizationService implements Purgeable {

    private final Map<String, WorkerpoolAuthorization> workerpoolAuthorizations;
    private final String schedulerPublicAddress;

    public WorkerpoolAuthorizationService(SchedulerConfiguration schedulerConfiguration) {
        schedulerPublicAddress = schedulerConfiguration.getSchedulerPublicAddress();
        workerpoolAuthorizations = ExpiringTaskMapFactory.getExpiringTaskMap();
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

        if (!isWorkerpoolAuthorizationValid(workerpoolAuthorization, schedulerPublicAddress)) {
            log.error("Cant putWorkerpoolAuthorization (invalid) [workerpoolAuthorization:{}]", workerpoolAuthorization);
            return false;
        }
        workerpoolAuthorizations.putIfAbsent(workerpoolAuthorization.getChainTaskId(), workerpoolAuthorization);
        return true;
    }

    WorkerpoolAuthorization getWorkerpoolAuthorization(String chainTaskId) {
        return workerpoolAuthorizations.get(chainTaskId);
    }

    /**
     * Try and remove workerpool authorization related to given task ID.
     *
     * @param chainTaskId Task ID whose related workerpool authorization should be purged
     * @return {@literal true} if key is not stored anymore,
     * {@literal false} otherwise.
     */
    @Override
    public boolean purgeTask(String chainTaskId) {
        workerpoolAuthorizations.remove(chainTaskId);
        return !workerpoolAuthorizations.containsKey(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        workerpoolAuthorizations.clear();
    }
}
