package com.iexec.worker.tee;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;

@Slf4j
public abstract class TeeService {
    private final SgxService sgxService;
    private final SmsService smsService;
    protected final TeeServicesPropertiesService teeServicesPropertiesService;

    protected TeeService(SgxService sgxService,
                         SmsService smsService,
                         TeeServicesPropertiesService teeServicesPropertiesService) {
        this.sgxService = sgxService;
        this.smsService = smsService;
        this.teeServicesPropertiesService = teeServicesPropertiesService;
    }

    public boolean isTeeEnabled() {
        return sgxService.isSgxEnabled();
    }

    public Optional<ReplicateStatusCause> areTeePrerequisitesMetForTask(String chainTaskId) {
        if (!isTeeEnabled()) {
            return Optional.of(TEE_NOT_SUPPORTED);
        }

        try {
            // Try to load the `SmsClient` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            smsService.getSmsClient(chainTaskId);
        } catch (SmsClientCreationException e) {
            log.error("Couldn't get SmsClient [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(UNKNOWN_SMS);
        }
        try {
            // Try to load the `TeeServicesProperties` relative to the task.
            // If it can't be loaded, then we won't be able to run the task.
            teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);
        } catch (RuntimeException e) {
            log.error("Couldn't get TeeServicesProperties [chainTaskId: {}]", chainTaskId, e);
            return Optional.of(GET_TEE_SERVICES_CONFIGURATION_FAILED);
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
            TeeSessionGenerationResponse session);

    public abstract List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session);

    public abstract List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session);

    public abstract Collection<String> getAdditionalBindings();
}
