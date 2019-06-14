package com.iexec.worker.tee.scone;

import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class SconeEnclaveSignature {

    private String result;
    private String resultHash;
    private String resultSalt;
    private String signature;
}