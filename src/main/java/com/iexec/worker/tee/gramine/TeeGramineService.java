package com.iexec.worker.tee.gramine;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.SmsClientProvider;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeWorkflowConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@Slf4j
public class TeeGramineService extends TeeService {
    private static final String SPS_URL_ENV_VAR = "sps";
    private static final String SPS_SESSION_ENV_VAR = "session";
    private static final String AESMD_SOCKET = "/var/run/aesmd/aesm.socket";

    public TeeGramineService(SgxService sgxService,
                             SmsClientProvider smsClientProvider,
                             TeeWorkflowConfigurationService teeWorkflowConfigurationService) {
        super(sgxService, smsClientProvider, teeWorkflowConfigurationService);
    }

    @Override
    public boolean isTeeEnabled() {
        return sgxService.isSgxEnabled();
    }

    @Override
    public boolean prepareTeeForTask(String chainTaskId) {
        // Nothing to do for a particular task
        return true;
    }

    @Override
    public List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session) {
        return getDockerEnv(session.getSessionId(), session.getSecretProvisioningUrl());
    }

    @Override
    public List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session) {
        return getDockerEnv(session.getSessionId(), session.getSecretProvisioningUrl());
    }

    @Override
    public List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session) {
        return getDockerEnv(session.getSessionId(), session.getSecretProvisioningUrl());
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        final List<String> bindings = new ArrayList<>();
        bindings.add(AESMD_SOCKET + ":" + AESMD_SOCKET);
        return bindings;
    }

    private List<String> getDockerEnv(String sessionId, String spsUrl) {
        return List.of(
                SPS_URL_ENV_VAR + "=" + spsUrl,
                SPS_SESSION_ENV_VAR + "=" + sessionId);
    }
}
