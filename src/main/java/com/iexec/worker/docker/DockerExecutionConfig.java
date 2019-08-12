package com.iexec.worker.docker;

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
public class DockerExecutionConfig {

    private String chainTaskId;
    // when containerName is not specified,
    // docker will generate one.
    private String containerName;
    private String imageUri;
    private String[] cmd;
    private List<String> env;
    private long maxExecutionTime;
    private String containerPort;
    private Map<String, String> bindPaths;
    private boolean isSgx;
}