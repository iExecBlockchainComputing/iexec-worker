package com.iexec.worker.pubsub;

import com.iexec.common.chain.ContributionAuthorization;
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
