package com.iexec.worker.tee;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public interface TeeAbstractService {
    boolean isTeeEnabled();

    List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    Collection<String> getBindings();
}
