/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee.scone;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeEnclaveConfiguration;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.worker.sgx.SgxService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesPropertiesService;
import com.iexec.worker.utils.LoggingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.TEE_PREPARATION_FAILED;

@Slf4j
@Service
public class TeeSconeService extends TeeService {

    private static final String SCONE_CAS_ADDR = "SCONE_CAS_ADDR";
    private static final String SCONE_LAS_ADDR = "SCONE_LAS_ADDR";
    private static final String SCONE_CONFIG_ID = "SCONE_CONFIG_ID";
    private static final String SCONE_HEAP = "SCONE_HEAP";
    private static final String SCONE_LOG = "SCONE_LOG";
    private static final String SCONE_VERSION = "SCONE_VERSION";

    private final LasServicesManager lasServicesManager;

    public TeeSconeService(
            SgxService sgxService,
            SmsService smsService,
            TeeServicesPropertiesService teeServicesPropertiesService,
            LasServicesManager lasServicesManager) {
        super(sgxService, smsService, teeServicesPropertiesService);
        this.lasServicesManager = lasServicesManager;

        if (isTeeEnabled()) {
            log.info("Worker can run TEE tasks");
        } else {
            LoggingUtils.printHighlightedMessage("Worker will not run TEE tasks");
        }
    }

    @Override
    public Optional<ReplicateStatusCause> areTeePrerequisitesMetForTask(String chainTaskId) {
        final Optional<ReplicateStatusCause> teePrerequisiteIssue = super.areTeePrerequisitesMetForTask(chainTaskId);
        if (teePrerequisiteIssue.isPresent()) {
            return teePrerequisiteIssue;
        }
        return prepareTeeForTask(chainTaskId) ? Optional.empty() : Optional.of(TEE_PREPARATION_FAILED);
    }

    @Override
    public boolean prepareTeeForTask(String chainTaskId) {
        return lasServicesManager.startLasService(chainTaskId);
    }

    @Override
    public List<String> buildPreComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session) {
        String sconeConfigId = session.getSessionId() + "/pre-compute";
        String chainTaskId = taskDescription.getChainTaskId();
        TeeServicesProperties properties =
                teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);
        return getDockerEnv(chainTaskId, sconeConfigId, properties.getPreComputeProperties().getHeapSizeInBytes(), session.getSecretProvisioningUrl());
    }

    @Override
    public List<String> buildComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session) {
        String sconeConfigId = session.getSessionId() + "/app";
        String chainTaskId = taskDescription.getChainTaskId();
        TeeEnclaveConfiguration enclaveConfig = taskDescription.getAppEnclaveConfiguration();
        long heapSize = enclaveConfig != null ? enclaveConfig.getHeapSize() : 0;
        return getDockerEnv(chainTaskId, sconeConfigId, heapSize, session.getSecretProvisioningUrl());
    }

    @Override
    public List<String> buildPostComputeDockerEnv(
            TaskDescription taskDescription,
            TeeSessionGenerationResponse session) {
        String sconeConfigId = session.getSessionId() + "/post-compute";
        String chainTaskId = taskDescription.getChainTaskId();
        TeeServicesProperties properties =
                teeServicesPropertiesService.getTeeServicesProperties(chainTaskId);
        return getDockerEnv(chainTaskId, sconeConfigId, properties.getPostComputeProperties().getHeapSizeInBytes(), session.getSecretProvisioningUrl());
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        return Collections.emptySet();
    }

    private List<String> getDockerEnv(String chainTaskId,
                                      String sconeConfigId,
                                      long sconeHeap,
                                      String casUrl) {
        final LasService las = lasServicesManager.getLas(chainTaskId);
        SconeConfiguration sconeConfig = las.getSconeConfig();

        String sconeVersion = sconeConfig.isShowVersion() ? "1" : "0";
        return List.of(
                SCONE_CAS_ADDR + "=" + casUrl,
                SCONE_LAS_ADDR + "=" + las.getUrl(),
                SCONE_CONFIG_ID + "=" + sconeConfigId,
                SCONE_HEAP + "=" + sconeHeap,   // TODO: remove sconeHeap in a next release
                SCONE_LOG + "=" + sconeConfig.getLogLevel(),
                SCONE_VERSION + "=" + sconeVersion);
    }
}
