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

package com.iexec.worker.pubsub;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.executor.TaskManagerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class SubscriptionServiceTests {

    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private TaskManagerService taskManagerService;

    @InjectMocks
    SubscriptionService subscriptionService;

    private static final String WORKER_WALLET_ADDRESS = "0x1234";
    private static final String CHAIN_TASK_ID = "chaintaskid";

    private TaskNotification notifTemplate = TaskNotification.builder()
            .workersAddress(Collections.singletonList(WORKER_WALLET_ADDRESS))
            .taskNotificationExtra(TaskNotificationExtra.builder()
                    .build())
            .chainTaskId(CHAIN_TASK_ID)
            .build();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        when(workerConfigurationService.getWorkerWalletAddress()).thenReturn(WORKER_WALLET_ADDRESS);


    }

}
