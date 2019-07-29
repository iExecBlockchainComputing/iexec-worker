package com.iexec.worker.docker;

import java.util.List;

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
    private String containerName;
    private String imageUri;
    private String[] cmd;
    private List<String> env;
    private long maxExecutionTime;
    private String containerPort;
}