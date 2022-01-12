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

import com.iexec.common.config.WorkerModel;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.config.CoreConfigurationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.tee.scone.TeeSconeService;
import com.iexec.worker.utils.version.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.web3j.crypto.Credentials;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkerServiceTests {

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
    private TeeSconeService teeSconeService;
    @Mock
    private RestartEndpoint restartEndpoint;
    @Mock
    private DockerService dockerService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);

    }

    @Test
    void shouldRegisterWorker() {
        String version = "version";
        String walletAddress = "walletAddress";
        String name = "name";
        String os = "os";
        String cpu = "cpu";
        int cpuNb = 4;
        int memorySize = 1024;
        boolean isTee = true;
        boolean isGpu = true;
        when(publicConfigService.getRequiredWorkerVersion()).thenReturn(version);
        when(versionService.getVersion()).thenReturn(version);
        Credentials credentials = mock(Credentials.class);
        when(workerConfigService.getWorkerName()).thenReturn(name);
        when(credentials.getAddress()).thenReturn(walletAddress);
        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(workerConfigService.getOS()).thenReturn(os);
        when(workerConfigService.getCPU()).thenReturn(cpu);
        when(workerConfigService.getCpuCount()).thenReturn(cpuNb);
        when(workerConfigService.getMemorySize()).thenReturn(memorySize);
        when(teeSconeService.isTeeEnabled()).thenReturn(isTee);
        when(workerConfigService.isGpuEnabled()).thenReturn(isGpu);
        when(workerConfigService.getHttpProxyHost()).thenReturn("host");
        when(workerConfigService.getHttpProxyPort()).thenReturn(1000);

        assertThat(workerService.registerWorker()).isTrue();

        ArgumentCaptor<WorkerModel> workerModelCaptor =
                ArgumentCaptor.forClass(WorkerModel.class);
        verify(customCoreFeignClient, times(1))
                .registerWorker(workerModelCaptor.capture());
        WorkerModel workerModel = workerModelCaptor.getValue();
        assertThat(workerModel.getName()).isEqualTo(name);
        assertThat(workerModel.getWalletAddress()).isEqualTo(walletAddress);
        assertThat(workerModel.getOs()).isEqualTo(os);
        assertThat(workerModel.getCpu()).isEqualTo(cpu);
        assertThat(workerModel.getCpuNb()).isEqualTo(cpuNb);
        assertThat(workerModel.getMemorySize()).isEqualTo(memorySize);
        assertThat(workerModel.isTeeEnabled()).isEqualTo(isTee);
        assertThat(workerModel.isGpuEnabled()).isEqualTo(isGpu);
    }

    @Test
    void shouldNotRegisterWorkerSinceBadVersion() {
        String version = "version";
        when(publicConfigService.getRequiredWorkerVersion()).thenReturn(version);
        when(versionService.getVersion()).thenReturn("someOtherVersion");

        assertThat(workerService.registerWorker()).isFalse();
        
        verify(customCoreFeignClient, times(0))
                .registerWorker(any());
    }

    @Test
    void shouldRestartGracefully() {
        when(customCoreFeignClient.getComputingTasks())
                .thenReturn(Collections.emptyList());

        workerService.restartGracefully();

        verify(dockerService, times(1))
                .stopAllRunningContainers();
        verify(restartEndpoint, times(1)).restart();
    }

    @Test
    void shouldNotRestartGracefullySinceComputingTasksInProgress() {
        when(customCoreFeignClient.getComputingTasks())
                .thenReturn(Collections.singletonList("chainTaskId"));

        workerService.restartGracefully();

        verify(dockerService, times(0))
                .stopAllRunningContainers();
        verify(restartEndpoint, times(0)).restart();
    }

}