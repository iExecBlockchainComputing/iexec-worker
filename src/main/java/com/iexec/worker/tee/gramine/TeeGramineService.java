package com.iexec.worker.tee.gramine;

import java.util.Collections;
import java.util.List;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeAbstractService;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;

@Service
public class TeeGramineService implements TeeAbstractService {
    private static final String SPS_URL = "SPS";
    private static final String SPS_SESSION = "SESSION";

    private final SgxService sgxService;

    public TeeGramineService(SgxService sgxService) {
        this.sgxService = sgxService;
    }

    @Override
    public boolean isTeeEnabled() {
        return sgxService.isSgxEnabled();
    }

    @Override
    public List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            @Nonnull TeeSessionGenerationResponse session) {
        // FIXME: implement PreCompute
        return Collections.emptyList();
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

    private List<String> getDockerEnv(String sessionId, String spsUrl) {
        return List.of(
                SPS_URL + "=" + spsUrl,
                SPS_SESSION + "=" + sessionId);
    }
}
