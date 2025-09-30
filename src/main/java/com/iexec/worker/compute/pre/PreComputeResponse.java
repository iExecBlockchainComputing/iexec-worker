/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.worker.compute.pre;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.compute.ComputeResponse;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PreComputeResponse implements ComputeResponse {

    @Builder.Default
    List<ReplicateStatusCause> exitCauses = List.of();
    boolean isTeeTask;
    TeeSessionGenerationResponse secureSession;
    String stdout;
    String stderr;


    @Override
    public boolean isSuccessful() {
        if (isTeeTask) {
            return ComputeResponse.super.isSuccessful() && secureSession != null;
        }
        return ComputeResponse.super.isSuccessful();
    }
}
