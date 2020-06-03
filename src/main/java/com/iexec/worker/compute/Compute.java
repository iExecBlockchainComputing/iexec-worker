package com.iexec.worker.compute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Compute {

    private String chainTaskId;
    private boolean isPreComputed;
    private boolean isComputed;
    private boolean isPostComputed;

    @Builder.Default
    private String secureSessionId = "";

    @Builder.Default
    private String stdout = "";

    public void appendToStdout(String message) {
        stdout = stdout + "\n" + message;
    }
}