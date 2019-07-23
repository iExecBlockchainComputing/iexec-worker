package com.iexec.worker.task;

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TaskEvent {

    private String chainTaskId;
    private ReplicateStatus status;
    private TaskNotificationType action;
}
