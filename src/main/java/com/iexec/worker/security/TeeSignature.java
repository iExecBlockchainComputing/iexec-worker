package com.iexec.worker.security;

import com.iexec.common.security.Signature;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TeeSignature {

    private String digest;
    private String hash;
    private String seal;
    private Signature sign;
}

