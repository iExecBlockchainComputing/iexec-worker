package com.iexec.worker.tee.gramine;

import java.util.List;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.tee.TeeAbstractService;
import org.springframework.stereotype.Service;

@Service
public class TeeGramineService implements TeeAbstractService {
    private final static String SPS_URL = "SPS";
    private final static String SPS_SESSION = "SESSION";

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
            TeeSessionGenerationResponse session) {
        // FIXME: implement PreCompute
        return null;
    }

    @Override
    public List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session) {
        return getDockerEnv(session.getSessionId(), session.getSecretProvisioningUrl());
    }

    @Override
    public List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session) {
        return getDockerEnv(session.getSessionId(), session.getSecretProvisioningUrl());
    }

    private List<String> getDockerEnv(String sessionId, String spsUrl) {
        return List.of(
                SPS_URL + "=" + spsUrl,
                SPS_SESSION + "=" + sessionId);
    }
}
