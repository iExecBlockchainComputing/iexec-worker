package com.iexec.worker.tee;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public interface TeeService {
    boolean isTeeEnabled();

    /**
     * Start any required service(s) to use TEE with selected technology for given task.
     *
     * @param chainTaskId Task whose service(s) should be started.
     * @return {@literal true} if all services have been correctly started, {@literal false} otherwise.
     */
    boolean prepareTeeForTask(String chainTaskId);

    List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    Collection<String> getAdditionalBindings();
}
