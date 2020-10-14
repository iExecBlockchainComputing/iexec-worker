/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.docker;

import com.iexec.worker.compute.ComputeResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerRunResponse implements ComputeResponse {

    private boolean isSuccessful;
    private DockerLogs dockerLogs;

    @Override
    public boolean isSuccessful() {
        return isSuccessful;
    }

    @Override
    public String getStdout() {
        if (dockerLogs != null && dockerLogs.getStdout() != null) {
            return dockerLogs.getStdout();
        }
        return "";
    }

    @Override
    public String getStderr() {
        if (dockerLogs != null && dockerLogs.getStderr() != null) {
            return dockerLogs.getStderr();
        }
        return "";
    }


}