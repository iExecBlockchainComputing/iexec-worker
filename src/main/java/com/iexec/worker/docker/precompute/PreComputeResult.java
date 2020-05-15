package com.iexec.worker.docker.precompute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreComputeResult {

    private boolean isSuccess;
    private String secureSessionId;

    public static PreComputeResult success() {
        return new PreComputeResult(true, "");
    }

    public static PreComputeResult success(String secureSessionId) {
        return new PreComputeResult(true, secureSessionId);
    }

    public static PreComputeResult failure() {
        return new PreComputeResult(false, "");
    }
}