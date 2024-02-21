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

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.pubsub.SubscriptionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Component
@AllArgsConstructor
@Slf4j
public class CompleteAction extends AbstractAction {
    private SubscriptionService subscriptionService;
    private PurgeService purgeService;
    private IexecHubService resultService;
    private ActionService actionService;

    @Override
    public TaskNotificationType getAction() {
        return TaskNotificationType.PLEASE_COMPLETE;
    }

    @Override
    public TaskNotificationType executeAction(TaskDescription taskDescription, TaskNotification notification) {
        final String chainTaskId = taskDescription.getChainTaskId();

        actionService.updateStatusAndGetNextAction(chainTaskId, COMPLETING);
        final ReplicateActionResponse actionResponse = complete(chainTaskId);
        subscriptionService.unsubscribeFromTopic(chainTaskId);
        final ReplicateStatus nextStatus = actionResponse.isSuccess() ? COMPLETED : COMPLETE_FAILED;
        return actionService.updateStatusAndGetNextAction(chainTaskId, nextStatus, actionResponse.getDetails());
    }

    ReplicateActionResponse complete(String chainTaskId) {
        purgeService.purgeAllServices(chainTaskId);

        if (!resultService.purgeTask(chainTaskId)) {
            return ReplicateActionResponse.failure();
        }

        return ReplicateActionResponse.success();
    }

}
