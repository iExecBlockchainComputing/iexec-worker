package com.iexec.worker.docker.postcompute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostComputeResult {
    

    private boolean isSuccess;
    private String stdout;

    public static PostComputeResult success(String stdout) {
        return new PostComputeResult(true, stdout);
    }

    public static PostComputeResult failure() {
        return new PostComputeResult(false, "");
    }
}