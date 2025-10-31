/*
 * Copyright 2022-2023 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.tee.gramine;

import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class TeeGramineService extends TeeService implements Purgeable {
    private static final String SPS_URL_ENV_VAR = "sps";
    private static final String SPS_SESSION_ENV_VAR = "session";
    private static final String AESMD_SOCKET = "/var/run/aesmd/aesm.socket";

    public TeeGramineService(SgxService sgxService,
                             SmsService smsService,
                             TeeServicesPropertiesService teeServicesPropertiesService) {
        super(sgxService, smsService, teeServicesPropertiesService);
    }

    @Override
    public boolean prepareTeeForTask(String chainTaskId) {
        // Nothing to do for a particular task
        return true;
    }

    @Override
    public List<String> buildPreComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(getTeeSession(taskDescription.getChainTaskId()));
    }

    @Override
    public List<String> buildComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(getTeeSession(taskDescription.getChainTaskId()));
    }

    @Override
    public List<String> buildPostComputeDockerEnv(final TaskDescription taskDescription) {
        return getDockerEnv(getTeeSession(taskDescription.getChainTaskId()));
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        final List<String> bindings = new ArrayList<>();
        bindings.add(AESMD_SOCKET + ":" + AESMD_SOCKET);
        return bindings;
    }

    private List<String> getDockerEnv(final TeeSessionGenerationResponse session) {
        return List.of(
                SPS_URL_ENV_VAR + "=" + session.getSecretProvisioningUrl(),
                SPS_SESSION_ENV_VAR + "=" + session.getSessionId());
    }

    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId:{}]", chainTaskId);
        return super.purgeTask(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        super.purgeAllTasksData();
    }
}
