package com.iexec.worker.tee.scone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SconeConfig {

    private String sconeCasAddress;
    private String sconeLasAddress;
    private String sconeConfigId;
    private String sconeHeap;

    @Builder.Default int sconeLog = 7;
    @Builder.Default int sconeVersion = 1;

    public SconeConfig(String sconeCasAddress, String sconeLasAddress, String sconeConfigId, String sconeHeap) {
        this.sconeCasAddress = sconeCasAddress;
        this.sconeLasAddress = sconeLasAddress;
        this.sconeConfigId = sconeConfigId;
        this.sconeHeap = sconeHeap;
        this.sconeLog = 7;
        this.sconeVersion = 1;
    }

    public ArrayList<String> toDockerEnv() {
        List<String> list = Arrays.asList(
            "SCONE_CAS_ADDR="   + sconeCasAddress,
            "SCONE_LAS_ADDR="   + sconeLasAddress,
            "SCONE_CONFIG_ID="  + sconeConfigId,
            "SCONE_HEAP="       + sconeHeap,
            "SCONE_LOG="        + sconeLog,
            "SCONE_VERSION="    + sconeVersion//,
            //"SCONE_MPROTECT="    + 1
        );

        return new ArrayList<String>(list);
    }
}