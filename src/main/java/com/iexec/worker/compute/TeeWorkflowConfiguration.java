/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.compute;

import com.iexec.common.tee.TeeWorkflowSharedConfiguration;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of tee workflow. It contains: pre-compute image, pre-compute
 * heap size, post-compute image, post-compute heap size. Images are downloaded
 * at initialization.
 */
@Configuration
public class TeeWorkflowConfiguration {

    @Getter
    private String preComputeImage;
    @Getter
    private long preComputeHeapSize;
    @Getter
    private String postComputeImage;
    @Getter
    private long postComputeHeapSize;

    public TeeWorkflowConfiguration(
            SconeTeeService sconeTeeService,
            SmsService smsService,
            DockerService dockerService) {
        if (!sconeTeeService.isTeeEnabled()) {
            return;
        }
        TeeWorkflowSharedConfiguration config = smsService.getTeeWorkflowConfiguration();
        if (config == null) {
            throw new RuntimeException("Missing tee workflow configuration");
        }
        if (!dockerService.getClient()
                .pullImage(config.getPreComputeImage())) {
            throw new RuntimeException("Failed to download pre-compute image");
        }
        if (dockerService.getClient()
                .pullImage(config.getPostComputeImage())) {
            throw new RuntimeException("Failed to download post-compute image");
        }
        preComputeImage = config.getPreComputeImage();
        preComputeHeapSize = config.getPreComputeHeapSize();
        postComputeImage = config.getPostComputeImage();
        postComputeHeapSize = config.getPostComputeHeapSize();
    }
}
