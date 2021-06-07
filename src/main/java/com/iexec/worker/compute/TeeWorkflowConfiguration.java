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
import com.iexec.worker.tee.scone.TeeSconeService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Configuration of tee workflow. It contains: pre-compute image, pre-compute
 * heap size, post-compute image, post-compute heap size. Images are downloaded
 * at initialization.
 */
@Slf4j
@Configuration
public class TeeWorkflowConfiguration {

    private final TeeSconeService teeSconeService;
    private final SmsService smsService;
    private final DockerService dockerService;

    @Getter
    private String preComputeImage = "";
    @Getter
    private long preComputeHeapSize = 0;
    @Getter
    private String preComputeEntrypoint = "";
    @Getter
    private String postComputeImage = "";
    @Getter
    private long postComputeHeapSize = 0;
    @Getter
    private String postComputeEntrypoint = "";

    public TeeWorkflowConfiguration(
            TeeSconeService teeSconeService,
            SmsService smsService,
            DockerService dockerService) {
        this.teeSconeService = teeSconeService;
        this.smsService = smsService;
        this.dockerService = dockerService;
    }

    /**
     * If the worker is TEE enabled, get configuration of pre/post compute
     * images and pull them for TEE tasks.
     * 
     * Note: /!\ the worker needs to be manually restarted if the configuration
     * on the SMS changes.
     */
    @PostConstruct
    private void pullPrePostComputeImages() {
        if (!teeSconeService.isTeeEnabled()) {
            return;
        }
        TeeWorkflowSharedConfiguration config =
                smsService.getTeeWorkflowConfiguration();
        log.info("Received tee workflow configuration [{}]", config);
        if (config == null) {
            throw new RuntimeException("Missing tee workflow configuration");
        }
        if (!dockerService.getClient()
                .pullImage(config.getPreComputeImage())) {
            throw new RuntimeException("Failed to download pre-compute image");
        }
        if (!dockerService.getClient()
                .pullImage(config.getPostComputeImage())) {
            throw new RuntimeException("Failed to download post-compute image");
        }
        preComputeImage = config.getPreComputeImage();
        preComputeHeapSize = config.getPreComputeHeapSize();
        preComputeEntrypoint = config.getPreComputeEntrypoint();
        postComputeImage = config.getPostComputeImage();
        postComputeHeapSize = config.getPostComputeHeapSize();
        postComputeEntrypoint = config.getPostComputeEntrypoint();
    }
}
