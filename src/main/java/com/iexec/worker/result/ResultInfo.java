package com.iexec.worker.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.worker.security.TeeSignature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultInfo {

    private String image;
    private String tag;
    private String cmd;
    private String containerId;//should be called processId
    private String deterministHash;
    @JsonIgnore
    private Optional<TeeSignature.Sign> enclaveSignature;

}
