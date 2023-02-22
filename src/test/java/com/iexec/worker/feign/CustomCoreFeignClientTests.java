/*
 * Copyright 2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.worker.feign.client.CoreClient;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomCoreFeignClientTests {
    private static final String AUTHORIZATION = "authorization";
    private static final String CHAIN_TASK_ID = "0x123";

    @Mock
    private LoginService loginService;
    @Mock
    private CoreClient coreClient;
    @InjectMocks
    private CustomCoreFeignClient customCoreFeignClient;

    @BeforeEach
    private void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region updateReplicateStatus
    @Test
    void shouldUpdateReplicatesStatus() {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken())
                .thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenReturn(ResponseEntity.of(Optional.of(TaskNotificationType.PLEASE_CONTINUE)));

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertThat(nextAction).isEqualTo(TaskNotificationType.PLEASE_CONTINUE);
    }

    @Test
    void shouldNotUpdateReplicatesStatusWhenBadLogin() {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken())
                .thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenThrow(FeignException.Unauthorized.class);

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertThat(nextAction).isNull();
        verify(loginService).login();
    }

    @Test
    void shouldNotUpdateReplicatesStatusWhenError() {
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate
                .builder()
                .status(ReplicateStatus.COMPLETING)
                .build();

        when(loginService.getToken())
                .thenReturn(AUTHORIZATION);
        when(coreClient.updateReplicateStatus(AUTHORIZATION, CHAIN_TASK_ID, statusUpdate))
                .thenThrow(FeignException.Forbidden.class);

        final TaskNotificationType nextAction = customCoreFeignClient.updateReplicateStatus(CHAIN_TASK_ID, statusUpdate);

        assertThat(nextAction).isNull();
        verify(loginService, never()).login();
    }
    // endregion
}
