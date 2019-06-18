package com.iexec.worker.tee.scone;

import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class SconeEnclaveSignatureFile {

    private String result;
    private String resultHash;
    private String resultSalt;
    private String signature;
}