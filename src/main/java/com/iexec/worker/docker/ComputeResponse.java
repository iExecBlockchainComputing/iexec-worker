package com.iexec.worker.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComputeResponse {
    
    private boolean isSuccess;
    private String stdout;

    public static ComputeResponse success(String stdout) {
        return new ComputeResponse(true, stdout);
    }

    public static ComputeResponse failure() {
        return new ComputeResponse(false, "");
    }
}