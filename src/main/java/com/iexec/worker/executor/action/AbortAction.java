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

package com.iexec.worker.executor.action;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.executor.AbortionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.iexec.common.replicate.ReplicateStatus.ABORTED;

@Component
@AllArgsConstructor
@Slf4j
public class AbortAction extends AbstractAction {
    private ActionService actionService;
    private AbortionService abortionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_ABORT;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        if (!abortionService.abort(chainTaskId)) {
            log.error("Failed to abort task [chainTaskId:{}]", chainTaskId);
            return null;
        }
        TaskAbortCause taskAbortCause = notification.getTaskAbortCause();
        ReplicateStatusCause replicateAbortCause = ReplicateStatusCause.getReplicateAbortCause(taskAbortCause);
        actionService.updateStatusAndGetNextAction(chainTaskId, ABORTED, replicateAbortCause);

        return null;
    }
}
