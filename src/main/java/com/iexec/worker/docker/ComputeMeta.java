package com.iexec.worker.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/* /!\ this probably should be split */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComputeMeta {
    
    private String chainTaskId;
    private boolean isSuccessfulCompute;
    private boolean isSuccessfulPreCompute;
    private boolean isSuccessfulPostCompute;
    @Builder.Default private String secureSessionId = "";
    @Builder.Default private String stdout = "";
}