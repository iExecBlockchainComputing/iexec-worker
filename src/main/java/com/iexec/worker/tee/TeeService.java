package com.iexec.worker.tee;

import com.iexec.common.chain.IexecHubAbstractService;
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
    private final SgxService sgxService;
    private final SmsClientProvider smsClientProvider;
    private final IexecHubAbstractService iexecHubService;
    protected final TeeServicesConfigurationService teeServicesConfigurationService;

    protected TeeService(SgxService sgxService,
                         SmsClientProvider smsClientProvider,
                         IexecHubAbstractService iexecHubService,
                         TeeServicesConfigurationService teeServicesConfigurationService) {
        this.sgxService = sgxService;
        this.smsClientProvider = smsClientProvider;
        this.iexecHubService = iexecHubService;
        this.teeServicesConfigurationService = teeServicesConfigurationService;
    }

    public boolean isTeeEnabled() {
        return sgxService.isSgxEnabled();
    }

    public Optional<ReplicateStatusCause> areTeePrerequisitesMetForTask(String chainTaskId) {
        if (!isTeeEnabled()) {
            return Optional.of(TEE_NOT_SUPPORTED);
        }

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        try {
            // Try to load the `SmsClient` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            smsClientProvider.getOrCreateSmsClientForTask(taskDescription);
        } catch (SmsClientCreationException e) {
            log.error("Couldn't get SmsClient [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(UNKNOWN_SMS);
        }
        try {
            // Try to load the `TeeServicesConfiguration` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            teeServicesConfigurationService.getTeeServicesConfiguration(chainTaskId);
        } catch (RuntimeException e) {
            log.error("Couldn't get TeeServicesConfiguration [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(GET_TEE_WORKFLOW_CONFIGURATION_FAILED);  // FIXME: update member name
        }

        return Optional.empty();
    }

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
