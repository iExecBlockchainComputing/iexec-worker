package com.iexec.worker.docker;

import com.iexec.common.utils.ArgsUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;


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
    private String cmd;
    private List<String> env;
    private long maxExecutionTime;
    private String containerPort;
    private Map<String, String> bindPaths;
    private boolean isSgx;

    private String getCmd() {
        return this.cmd;
    }

    public String getStringArgsCmd() {
        return this.cmd;
    }

    public String[] getArrayArgsCmd() {
        return ArgsUtils.stringArgsToArrayArgs(this.cmd);
    }

}