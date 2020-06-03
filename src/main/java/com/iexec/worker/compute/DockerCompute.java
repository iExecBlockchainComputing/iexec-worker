package com.iexec.worker.compute;

import java.util.List;
import java.util.Map;

import com.iexec.common.utils.ArgsUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerCompute {

    private String chainTaskId;
    private String containerName;
    private String containerId;
    private String containerPort;
    private String imageUri;
    private String cmd;
    private List<String> env;
    private long maxExecutionTime;
    private Map<String, String> bindPaths;
    private boolean isSgx;

    public String getStringArgsCmd() {
        return getCmd();
    }

    public String[] getArrayArgsCmd() {
        return ArgsUtils.stringArgsToArrayArgs(getCmd());
    }
}
