package com.iexec.worker.security;

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
    private Sign sign;

    public TeeSignature(String digest, String hash, String seal, Integer v, String r, String s){
        this.digest = digest;
        this.hash = hash;
        this.seal = seal;
        this.setSign(new Sign(v, r, s));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public class Sign {

        private Integer v;
        private String r;
        private String s;

    }

}

