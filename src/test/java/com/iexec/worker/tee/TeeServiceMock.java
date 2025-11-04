/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.tee;

import com.github.dockerjava.api.model.Device;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.sms.SmsService;

import java.util.Collection;
import java.util.List;

class TeeServiceMock extends TeeService {

    protected TeeServiceMock(SmsService smsService,
                             TeeServicesPropertiesService teeServicesPropertiesService) {
        super(smsService, teeServicesPropertiesService);
    }

    @Override
    public boolean isTeeEnabled() {
        return true;
    }

    @Override
    public boolean prepareTeeForTask(String chainTaskId) {
        return false;
    }

    @Override
    public List<String> buildPreComputeDockerEnv(TaskDescription taskDescription) {
        return null;
    }

    @Override
    public List<String> buildComputeDockerEnv(TaskDescription taskDescription) {
        return null;
    }

    @Override
    public List<String> buildPostComputeDockerEnv(TaskDescription taskDescription) {
        return null;
    }

    @Override
    public Collection<String> getAdditionalBindings() {
        return List.of();
    }

    @Override
    public List<Device> getDevices() {
        return List.of();
    }
}
