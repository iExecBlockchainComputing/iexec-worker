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

package com.iexec.worker.compute;

import com.iexec.worker.docker.DockerRunResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComputeResponsesHolder {

    private String chainTaskId;
    private DockerRunResponse preComputeDockerRunResponse;
    private DockerRunResponse computeDockerRunResponse;
    private DockerRunResponse postComputeDockerRunResponse;

    @Builder.Default
    private String secureSessionId = "";

    public String appendToStdout(String message) {
        return message + "\n" + message;
    }

    public String getStdout() {
        String stdout = "";
        if (preComputeDockerRunResponse != null && !preComputeDockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(preComputeDockerRunResponse.getStdout());
        }

        if (computeDockerRunResponse != null && !computeDockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(computeDockerRunResponse.getStdout());
        }

        if (postComputeDockerRunResponse != null && !postComputeDockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(postComputeDockerRunResponse.getStdout());
        }
        return stdout;
    }

    public boolean isPreComputed() {
        return preComputeDockerRunResponse != null && preComputeDockerRunResponse.isSuccessful();
    }

    public boolean isComputed() {
        return computeDockerRunResponse != null && computeDockerRunResponse.isSuccessful();
    }

    public boolean isPostComputed() {
        return postComputeDockerRunResponse != null && postComputeDockerRunResponse.isSuccessful();
    }
}