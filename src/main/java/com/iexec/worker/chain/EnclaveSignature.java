package com.iexec.worker.chain;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EnclaveSignature {

    private String hash;
    private Sign sign;
    private String digest;
    private String seal;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public class Sign {

        private String r;
        private Integer v;
        private String s;

    }

}

