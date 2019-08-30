package com.iexec.worker.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultUploadDetails {

    private String resultLink;
    private String chainCallbackData;
}