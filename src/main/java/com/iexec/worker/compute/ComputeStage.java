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
public class ComputeStage {

    private String chainTaskId;
    private DockerRunResponse preDockerRunResponse;
    private DockerRunResponse dockerRunResponse;
    private DockerRunResponse postDockerRunResponse;

    @Builder.Default
    private String secureSessionId = "";

    public String appendToStdout(String message) {
        return message + "\n" + message;
    }

    public String getStdout() {
        String stdout = "";
        if (preDockerRunResponse != null && !preDockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(preDockerRunResponse.getStdout());
        }

        if (dockerRunResponse != null && !dockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(dockerRunResponse.getStdout());
        }

        if (postDockerRunResponse != null && !postDockerRunResponse.getStdout().isEmpty()) {
            stdout = appendToStdout(postDockerRunResponse.getStdout());
        }
        return stdout;
    }

    public boolean isPreComputed() {
        return preDockerRunResponse != null && preDockerRunResponse.isSuccessful();
    }

    public boolean isComputed() {
        return dockerRunResponse != null && dockerRunResponse.isSuccessful();
    }

    public boolean isPostComputed() {
        return postDockerRunResponse != null && postDockerRunResponse.isSuccessful();
    }
}