/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License; Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing; software
 * distributed under the License is distributed on an "AS IS" BASIS;
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND; either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.worker;

import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.version.VersionService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.context.restart.RestartEndpoint;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class WorkerServiceTests {

    public static final String SESSION_ID = "SESSION_ID";
    public static final String OTHER_SESSION_ID = "OTHER_SESSION_ID";

    @InjectMocks
    private WorkerService workerService;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private CoreConfigurationService coreConfigService;
    @Mock
    private PublicConfigurationService publicConfigService;
    @Mock
    private CustomCoreFeignClient customCoreFeignClient;
    @Mock
    private VersionService versionService;
    @Mock
    private SconeTeeService sconeTeeService;
    @Mock
    private RestartEndpoint restartEndpoint;
    @Mock
    private DockerService dockerService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

    }

    @Test
    public void shouldRestartGracefully() {
        when(customCoreFeignClient.getComputingTasks())
                .thenReturn(Collections.emptyList());

        workerService.restartGracefully();

        verify(dockerService, times(1))
                .stopRunningContainers();
        verify(restartEndpoint, times(1)).restart();
    }

    @Test
    public void shouldNotRestartGracefullySinceComputingTasksInProgress() {
        when(customCoreFeignClient.getComputingTasks())
                .thenReturn(Collections.singletonList("chainTaskId"));

        workerService.restartGracefully();

        verify(dockerService, times(0))
                .stopRunningContainers();
        verify(restartEndpoint, times(0)).restart();
    }

}