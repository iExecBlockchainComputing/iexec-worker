package com.iexec.worker.tee;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

class TeeServiceMock extends TeeService {

    protected TeeServiceMock(SgxService sgxService,
                             SmsService smsService,
                             TeeServicesPropertiesService teeServicesPropertiesService) {
        super(sgxService, smsService, teeServicesPropertiesService);
    }

    @Override
    public boolean prepareTeeForTask(String chainTaskId) {
        return false;
    }

    @Override
    public List<String> buildPreComputeDockerEnv(TaskDescription taskDescription, @NotNull TeeSessionGenerationResponse session) {
        return null;
    }

    @Override
    public List<String> buildComputeDockerEnv(TaskDescription taskDescription, @NotNull TeeSessionGenerationResponse session) {
        return null;
    }

    @Override
    public List<String> buildPostComputeDockerEnv(TaskDescription taskDescription, @NotNull TeeSessionGenerationResponse session) {
        return null;
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        return null;
    }
}
