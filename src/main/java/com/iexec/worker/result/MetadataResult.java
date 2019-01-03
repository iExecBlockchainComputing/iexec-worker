package com.iexec.worker.result;

import com.iexec.common.security.Signature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetadataResult {

    private String image;
    private String tag;
    private String cmd;
    private String containerId;//should be called processId
    private String deterministHash;
    private Signature executionEnclaveSignature;

}
