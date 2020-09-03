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

import java.util.List;
import java.util.Map;

import com.iexec.common.utils.ArgsUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerCompute {

    private String chainTaskId;
    private String containerName;
    private String containerId;
    private String containerPort;
    private String imageUri;
    private String cmd;
    private List<String> env;
    private long maxExecutionTime;
    private Map<String, String> bindPaths;
    private boolean isSgx;

    public String getStringArgsCmd() {
        return cmd;
    }

    public String[] getArrayArgsCmd() {
        return ArgsUtils.stringArgsToArrayArgs(cmd);
    }
}
