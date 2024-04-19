/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.replicate.ReplicateTaskSummary;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.*;

class CustomCoreFeignClientTests {
    private static final String AUTHORIZATION = "authorization";
    private static final String CHAIN_TASK_ID = "0x123";
    private static final long BLOCK_NUMBER = 123456789;

    @Mock
    private LoginService loginService;
    @Mock
    private CoreClient coreClient;
    @InjectMocks
    private CustomCoreFeignClient customCoreFeignClient;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    //region getCoreVersion
    @Test
    void shouldGetCoreVersion() {
        verifyNoInteractions(loginService);
        when(coreClient.getCoreVersion()).thenReturn("X.Y.Z");
        assertAll(
                () -> assertThat(customCoreFeignClient.getCoreVersion()).isEqualTo("X.Y.Z"),
                () -> verifyNoInteractions(loginService)
        );
    }

    @Test
    void shouldNotGetCoreVersionWhenError() {
        when(coreClient.getCoreVersion()).thenThrow(FeignException.class);
        assertAll(
                () -> assertThat(customCoreFeignClient.getCoreVersion()).isNull(),
                () -> verifyNoInteractions(loginService)
        );
    }
    //endregion

    // region getPublicConfiguration
    @Test
    void shouldGetPublicConfiguration() {
        PublicConfiguration publicConfiguration = PublicConfiguration.builder()
                .requiredWorkerVersion("X.Y.Z")
                .build();
        when(coreClient.getPublicConfiguration()).thenReturn(publicConfiguration);
        assertAll(
                () -> assertThat(customCoreFeignClient.getPublicConfiguration()).isEqualTo(publicConfiguration),
                () -> verifyNoInteractions(loginService)
        );
    }

    @Test
    void shouldNotGetPublicConfigurationWhenError() {
        when(coreClient.getPublicConfiguration()).thenThrow(FeignException.class);
        assertAll(
                () -> assertThat(customCoreFeignClient.getPublicConfiguration()).isNull(),
                () -> verifyNoInteractions(loginService)
        );
    }
    // endregion

    // region registerWorker
    @Test
    void shouldRegisterWorker() {
        final WorkerModel model = WorkerModel.builder().build();
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        customCoreFeignClient.registerWorker(model);
        verify(loginService, never()).login();
    }

    @Test
    void shouldNotRegisterWhenUnauthorized() {
        final WorkerModel model = WorkerModel.builder().build();
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(loginService.login()).thenReturn(AUTHORIZATION);
        when(coreClient.registerWorker(AUTHORIZATION, model)).thenThrow(FeignException.Unauthorized.class);
        customCoreFeignClient.registerWorker(model);
        verify(loginService, times(3)).login();
    }
    // endregion

    // region getComputingTasks
    @Test
    void shouldGetComputingTasks() {
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getComputingTasks(AUTHORIZATION)).thenReturn(List.of(CHAIN_TASK_ID));
        final List<String> tasks = customCoreFeignClient.getComputingTasks();
        assertAll(
                () -> assertThat(tasks).containsExactly(CHAIN_TASK_ID),
                () -> verify(loginService, never()).login()
        );
    }

    @Test
    void shouldNotGetComputingTasksWhenBadLogin() {
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(loginService.login()).thenReturn(AUTHORIZATION);
        when(coreClient.getComputingTasks(AUTHORIZATION)).thenThrow(FeignException.Unauthorized.class);
        final List<String> tasks = customCoreFeignClient.getComputingTasks();
        assertAll(
                () -> assertThat(tasks).isEmpty(),
                () -> verify(loginService, times(3)).login()
        );
    }

    @Test
    void shouldNotGetComputingTasksWhenError() {
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getComputingTasks(AUTHORIZATION)).thenThrow(FeignException.class);
        final List<String> tasks = customCoreFeignClient.getComputingTasks();
        assertAll(
                () -> assertThat(tasks).isEmpty(),
                () -> verify(loginService, never()).login()
        );
    }
    // endregion

    // region getMissedTaskNotifications
    @Test
    void shouldGetMissedTaskNotifications() {
        final List<TaskNotification> missedNotifications = List.of(TaskNotification.builder().build());
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getMissedTaskNotifications(AUTHORIZATION, BLOCK_NUMBER)).thenReturn(missedNotifications);
        final List<TaskNotification> notifications = customCoreFeignClient.getMissedTaskNotifications(BLOCK_NUMBER);
        assertAll(
                () -> assertThat(notifications).isEqualTo(missedNotifications),
                () -> verify(loginService, never()).login()
        );
    }

    @Test
    void shouldNotGetMissedTaskNotificationsWhenUnauthorized() {
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(loginService.login()).thenReturn(AUTHORIZATION);
        when(coreClient.getMissedTaskNotifications(AUTHORIZATION, BLOCK_NUMBER)).thenThrow(FeignException.Unauthorized.class);
        final List<TaskNotification> notifications = customCoreFeignClient.getMissedTaskNotifications(BLOCK_NUMBER);
        assertAll(
                () -> assertThat(notifications).isEmpty(),
                () -> verify(loginService, times(3)).login()
        );
    }

    @Test
    void shouldNotGetMissedTaskNotificationsWhenError() {
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getMissedTaskNotifications(AUTHORIZATION, BLOCK_NUMBER)).thenThrow(FeignException.class);
        final List<TaskNotification> notifications = customCoreFeignClient.getMissedTaskNotifications(BLOCK_NUMBER);
        assertAll(
                () -> assertThat(notifications).isEmpty(),
                () -> verify(loginService, never()).login()
        );
    }
    // endregion

    // region getAvailableReplicateTaskSummary
    @Test
    void shouldGetAvailableReplicateTaskSummary() {
        final long blockNumber = 0L;
        final ReplicateTaskSummary replicateTaskSummary = ReplicateTaskSummary.builder()
                .build();
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getAvailableReplicateTaskSummary(AUTHORIZATION, blockNumber)).thenReturn(replicateTaskSummary);
        Optional<ReplicateTaskSummary> result = customCoreFeignClient.getAvailableReplicateTaskSummary(blockNumber);
        assertAll(
                () -> assertThat(result).contains(replicateTaskSummary),
                () -> verify(loginService, never()).login()
        );
    }

    @Test
    void shouldNotGetAvailableReplicateTaskSummaryWhenBadLogin() {
        final long blockNumber = 0L;
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getAvailableReplicateTaskSummary(AUTHORIZATION, blockNumber)).thenThrow(FeignException.Unauthorized.class);
        Optional<ReplicateTaskSummary> result = customCoreFeignClient.getAvailableReplicateTaskSummary(blockNumber);
        assertAll(
                () -> assertThat(result).isEmpty(),
                () -> verify(loginService).login()
        );
    }

    @Test
    void shouldNotGetAvailableReplicateTaskSummaryWhenError() {
        final long blockNumber = 0L;
        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.getAvailableReplicateTaskSummary(AUTHORIZATION, blockNumber)).thenThrow(FeignException.class);
        Optional<ReplicateTaskSummary> result = customCoreFeignClient.getAvailableReplicateTaskSummary(blockNumber);
        assertAll(
                () -> assertThat(result).isEmpty(),
                () -> verify(loginService, never()).login()
        );
    }
    // endregion

    // region updateReplicateStatus
    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"OK", "ALREADY_REPORTED"})
    void shouldUpdateReplicatesStatus(HttpStatus status) {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenReturn(TaskNotificationType.PLEASE_CONTINUE);

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertThat(nextAction).isEqualTo(TaskNotificationType.PLEASE_CONTINUE);
    }

    @Test
    void shouldNotUpdateReplicatesStatusWhenBadLogin() {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenThrow(FeignException.Unauthorized.class);

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertAll(
                () -> assertThat(nextAction).isNull(),
                () -> verify(loginService).login()
        );
    }

    @Test
    void shouldNotUpdateReplicatesStatusWhenError() {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken()).thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenThrow(FeignException.Forbidden.class);

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertAll(
                () -> assertThat(nextAction).isNull(),
                () -> verify(loginService, never()).login()
        );
    }
    // endregion
}
