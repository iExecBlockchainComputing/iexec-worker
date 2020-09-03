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

package com.iexec.worker.tee.scone;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SconeConfig {

    private String sconeCasAddress;
    private String sconeLasAddress;
    private String sconeConfigId;
    private String sconeHeap;

    @Builder.Default int sconeLog = 7;
    @Builder.Default int sconeVersion = 1;

    public SconeConfig(String sconeCasAddress, String sconeLasAddress, String sconeConfigId, String sconeHeap) {
        this.sconeCasAddress = sconeCasAddress;
        this.sconeLasAddress = sconeLasAddress;
        this.sconeConfigId = sconeConfigId;
        this.sconeHeap = sconeHeap;
        this.sconeLog = 7;
        this.sconeVersion = 1;
    }

    public List<String> toDockerEnv() {
        List<String> list = new ArrayList<>();
        list.add("SCONE_CAS_ADDR=" + sconeCasAddress);
        list.add("SCONE_LAS_ADDR="   + sconeLasAddress);
        list.add("SCONE_CONFIG_ID="  + sconeConfigId);
        list.add("SCONE_HEAP="       + sconeHeap);
        list.add("SCONE_LOG="        + sconeLog);
        list.add("SCONE_VERSION="    + sconeVersion);
        // list.add("SCONE_MPROTECT="   + 1);
        return list;
    }
}