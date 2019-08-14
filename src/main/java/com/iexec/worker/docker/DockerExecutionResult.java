package com.iexec.worker.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerExecutionResult {

    private boolean isSuccess;
    private String stdout;
    private String containerName;

    public static DockerExecutionResult success(String stdout, String containerName) {
        return DockerExecutionResult.builder()
                .isSuccess(true)
                .stdout(stdout)
                .containerName(containerName)
                .build();
    }

    public static DockerExecutionResult failure() {
        return DockerExecutionResult.builder()
                .isSuccess(false)
                .stdout("")
                .containerName("")
                .build();
    }
}