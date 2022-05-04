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

package com.iexec.worker.compute.pre;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.tee.TeeSessionGenerationError;
import com.iexec.worker.compute.ComputeResponse;
import lombok.*;

@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PreComputeResponse implements ComputeResponse {

    private boolean isSuccessful;
    private boolean isTeeTask;
    private String secureSessionId;
    private String stdout;
    private String stderr;

    // Only zero or one of `exitCause` or `teeSessionGenerationError`
    // should be non-null, not both.
    private ReplicateStatusCause exitCause;
    private TeeSessionGenerationError teeSessionGenerationError;

    public boolean isSuccessful() {
        if (isTeeTask) {
            return !secureSessionId.isEmpty();
        }
        return isSuccessful;
    }

    public boolean failedOnTeeSessionGeneration() {
        return teeSessionGenerationError != null;
    }
}