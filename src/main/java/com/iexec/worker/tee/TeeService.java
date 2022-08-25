package com.iexec.worker.tee;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;

@Slf4j
public abstract class TeeService {
    protected final SgxService sgxService;
    protected final SmsClientProvider smsClientProvider;
    protected final TeeWorkflowConfigurationService teeWorkflowConfigurationService;

    protected TeeService(SgxService sgxService,
                         SmsClientProvider smsClientProvider,
                         TeeWorkflowConfigurationService teeWorkflowConfigurationService) {
        this.sgxService = sgxService;
        this.smsClientProvider = smsClientProvider;
        this.teeWorkflowConfigurationService = teeWorkflowConfigurationService;
    }

    public Optional<ReplicateStatusCause> areTeePrerequisitesMetForTask(String chainTaskId) {
        if (!isTeeEnabled()) {
            return Optional.of(TEE_NOT_SUPPORTED);
        }

        try {
            // Try to load the `SmsClient` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            smsClientProvider.getOrCreateSmsClientForTask(chainTaskId);
        } catch (SmsClientCreationException e) {
            log.error("Couldn't get SmsClient [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(UNKNOWN_SMS);
        }
        try {
            // Try to load the `TeeWorkflowConfiguration` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            teeWorkflowConfigurationService.getOrCreateTeeWorkflowConfiguration(chainTaskId);
        } catch (RuntimeException e) {
            log.error("Couldn't get TeeWorkflowConfiguration [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(GET_TEE_WORKFLOW_CONFIGURATION_FAILED);
        }

        return Optional.empty();
    }

    public abstract boolean isTeeEnabled();

    /**
     * Start any required service(s) to use TEE with selected technology for given task.
     *
     * @param chainTaskId Task whose service(s) should be started.
     * @return {@literal true} if all services have been correctly started, {@literal false} otherwise.
     */
    public abstract boolean prepareTeeForTask(String chainTaskId);

    public abstract List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    public abstract List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    public abstract List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session);

    public abstract Collection<String> getAdditionalBindings();
}
