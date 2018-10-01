package com.iexec.worker.docker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerResult {

    private String image;
    private String tag;
    private String cmd;
    private String containerId;
    private String stdout;
}
