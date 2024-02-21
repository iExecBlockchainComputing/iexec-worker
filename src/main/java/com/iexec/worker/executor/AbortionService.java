/*
 * Copyright 2024-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.executor;

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
@AllArgsConstructor
@Slf4j
public class AbortionService {
    private DockerService dockerService;
    private SubscriptionService subscriptionService;
    private PurgeService purgeService;

    public boolean abort(String chainTaskId) {
        log.info("Aborting task [chainTaskId:{}]", chainTaskId);
        Predicate<String> containsChainTaskId = name -> name.contains(chainTaskId);
        dockerService.stopRunningContainersWithNamePredicate(containsChainTaskId);
        log.info("Stopped task containers [chainTaskId:{}]", chainTaskId);
        subscriptionService.unsubscribeFromTopic(chainTaskId);
        boolean isSuccess = purgeService.purgeAllServices(chainTaskId);
        if (!isSuccess) {
            log.error("Failed to abort task [chainTaskId:{}]", chainTaskId);
        }
        return isSuccess;
    }
}
