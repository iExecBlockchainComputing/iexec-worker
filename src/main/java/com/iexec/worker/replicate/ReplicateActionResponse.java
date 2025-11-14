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

package com.iexec.worker.replicate;

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.commons.poco.chain.ChainReceipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicateActionResponse {
    private boolean isSuccess;
    private ReplicateStatusDetails details;

    public static ReplicateActionResponse success() {
        return new ReplicateActionResponse(true, null);
    }

    public static ReplicateActionResponse success(final ChainReceipt chainReceipt) {
        return new ReplicateActionResponse(
                true, ReplicateStatusDetails.builder().chainReceipt(chainReceipt).build());
    }

    public static ReplicateActionResponse success(final String resultLink, final String callbackData) {
        final ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(callbackData)
                .build();
        return new ReplicateActionResponse(true, details);
    }

    public static ReplicateActionResponse successWithDetails(final ReplicateStatusDetails details) {
        return new ReplicateActionResponse(true, details);
    }

    public static ReplicateActionResponse failure() {
        return new ReplicateActionResponse(false, null);
    }

    public static ReplicateActionResponse failure(final ReplicateStatusCause cause) {
        return new ReplicateActionResponse(
                false, ReplicateStatusDetails.builder().cause(cause).build());
    }
}
