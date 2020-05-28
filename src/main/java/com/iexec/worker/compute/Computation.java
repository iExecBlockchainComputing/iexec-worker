package com.iexec.worker.compute;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Computation {

    // meta
    private String chainTaskId;
    private boolean isComputed;
    private boolean isPreComputed;
    private boolean isPostComputed;
    @Builder.Default private String secureSessionId = "";
    @Builder.Default private String stdout = "";

    // containerInfo
    private String containerId;
    private String containerName;

    // public class DockerExecutionConfig {
    private String imageUri;
    private String cmd;
    private List<String> env;
    private long maxExecutionTime;
    private String containerPort;
    private Map<String, String> bindPaths;
    private boolean isSgx;

    // public class DockerExecutionResult {
    private boolean isSuccess;
}