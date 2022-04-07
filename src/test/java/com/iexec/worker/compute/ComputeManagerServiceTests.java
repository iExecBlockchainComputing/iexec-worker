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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.docker.DockerLogs;
import com.iexec.common.docker.client.DockerClientInstance;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.app.AppComputeService;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.post.PostComputeService;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.compute.pre.PreComputeService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.docker.DockerRegistryConfiguration;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.result.ResultService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

class ComputeManagerServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String APP_URI = "APP_URI";
    private final static String TEE_POST_COMPUTE_IMAGE =
            "TEE_POST_COMPUTE_IMAGE";
    private final static String SECURE_SESSION_ID = "SECURE_SESSION_ID";
    private final static long MAX_EXECUTION_TIME = 1000;

    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .appType(DappType.DOCKER)
            .appUri(APP_URI)
            .datasetUri(DATASET_URI)
            .teePostComputeImage(TEE_POST_COMPUTE_IMAGE)
            .maxExecutionTime(MAX_EXECUTION_TIME)
            .developerLoggerEnabled(true)
            .inputFiles(Arrays.asList("file0", "file1"))
            .isTeeTask(true)
            .maxExecutionTime(3000)
            .build();
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .build();
    private final DockerLogs dockerLogs =
            DockerLogs.builder().stdout("stdout").stderr("stderr").build();

    @TempDir
    public File jUnitTemporaryFolder;

    @InjectMocks
    private ComputeManagerService computeManagerService;
    @Mock
    private DockerService dockerService;
    @Mock
    private DockerRegistryConfiguration dockerRegistryConfiguration;
    @Mock
    private DockerClientInstance dockerClient;
    @Mock
    private PreComputeService preComputeService;
    @Mock
    private AppComputeService appComputeService;
    @Mock
    private PostComputeService postComputeService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ResultService resultService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldDownloadApp() {
        when(dockerRegistryConfiguration.getMinPullTimeout()).thenReturn(Duration.of(5, ChronoUnit.MINUTES));
        when(dockerRegistryConfiguration.getMaxPullTimeout()).thenReturn(Duration.of(30, ChronoUnit.MINUTES));
        when(dockerService.getClient(taskDescription.getAppUri())).thenReturn(dockerClient);
        when(dockerClient.pullImage(taskDescription.getAppUri(), Duration.of(7, ChronoUnit.MINUTES))).thenReturn(true);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isTrue();
    }

    @Test
    void shouldNotDownloadAppSincePullImageFailed() {
        when(dockerService.getClient(taskDescription.getAppUri())).thenReturn(dockerClient);
        when(dockerClient.pullImage(taskDescription.getAppUri())).thenReturn(false);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceNoTaskDescription() {
        Assertions.assertThat(computeManagerService.downloadApp(null)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceNoAppType() {
        taskDescription.setAppType(null);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceWrongAppType() {
        taskDescription.setAppType(DappType.BINARY);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldHaveImageDownloaded() {
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(true);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isTrue();
    }

    @Test
    void shouldNotHaveImageDownloaded() {
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(false);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isFalse();
    }

    // pre compute

    @Test
    void shouldRunStandardPreCompute() {
        taskDescription.setTeeTask(false);
        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);

        Assertions.assertThat(preComputeResponse.isSuccessful()).isTrue();
    }

    @Test
    void shouldRunTeePreCompute() {
        taskDescription.setTeeTask(true);
        when(preComputeService.runTeePreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn(SECURE_SESSION_ID);

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(preComputeResponse.getSecureSessionId()).isEqualTo(SECURE_SESSION_ID);
        verify(preComputeService, times(1))
                .runTeePreCompute(taskDescription,
                        workerpoolAuthorization);
    }

    @Test
    void shouldRunTeePreComputeWithFailureResponse() {
        taskDescription.setTeeTask(true);
        when(preComputeService.runTeePreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn("");

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(preComputeResponse.getSecureSessionId()).isEmpty();
    }

    // compute

    @Test
    void shouldRunStandardCompute() throws IOException {
        taskDescription.setTeeTask(false);
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .isSuccessful(true)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, ""))
                .thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.getAbsolutePath());

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, "");
        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(appComputeService, times(1))
                .runCompute(taskDescription,
                        "");
    }

    @Test
    void shouldRunStandardComputeWithFailureResponse() {
        taskDescription.setTeeTask(false);
        AppComputeResponse expectedDockerRunResponse =
        AppComputeResponse.builder()
                        .isSuccessful(false)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, ""))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, "");
        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }

    @Test
    void shouldRunTeeCompute() throws IOException {
        taskDescription.setTeeTask(true);
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .isSuccessful(true)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription,
                SECURE_SESSION_ID)).thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.getAbsolutePath());

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription,
                        SECURE_SESSION_ID);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(appComputeService, times(1))
                .runCompute(taskDescription,
                        SECURE_SESSION_ID);
    }

    @Test
    void shouldRunTeeComputeWithFailure() {
        taskDescription.setTeeTask(true);
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .isSuccessful(false)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, SECURE_SESSION_ID))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription,
                        SECURE_SESSION_ID);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }

    // pre compute

    @Test
    void shouldRunStandardPostCompute() {
        taskDescription.setTeeTask(false);
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(true);
        ComputedFile computedFile = mock(ComputedFile.class);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription, "");
        Assertions.assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(postComputeService, times(1))
                .runStandardPostCompute(taskDescription);
        verify(resultService, times(1))
                .saveResultInfo(anyString(), any(), any());
    }

    @Test
    void shouldRunStandardPostComputeWithFailureResponse() {
        taskDescription.setTeeTask(false);
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(false);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        "");
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
    }

    @Test
    void shouldRunTeePostCompute() {
        taskDescription.setTeeTask(true);
        PostComputeResponse expectedDockerRunResponse =
                PostComputeResponse.builder()
                        .isSuccessful(true)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION_ID))
                .thenReturn(expectedDockerRunResponse);
        ComputedFile computedFile = mock(ComputedFile.class);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION_ID);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(postComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(postComputeService, times(1))
                .runTeePostCompute(taskDescription, SECURE_SESSION_ID);
        verify(resultService, times(1))
                .saveResultInfo(anyString(), any(), any());
    }

    @Test
    void shouldRunTeePostComputeWithFailureResponse() {
        taskDescription.setTeeTask(true);
        PostComputeResponse expectedDockerRunResponse =
                PostComputeResponse.builder()
                        .isSuccessful(false)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION_ID))
                .thenReturn(expectedDockerRunResponse);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION_ID);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(postComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }

    // computeImagePullTimeout
    static Stream<Arguments> computeImagePullTimeoutValues() {
        return Stream.of(
                // maxExecutionTime, minPullTimeout, maxPullTimeout, expectedTimeout

                // Default values
                Arguments.of(3000  , Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 7),        // XS category
                Arguments.of(12000 , Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 13),      // S category
                Arguments.of(36000 , Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 18),      // M category
                Arguments.of(108000, Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 23),     // L category
                Arguments.of(360000, Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 28),     // XL category

                // Unusual timeouts
                Arguments.of(3000, Duration.of(1, ChronoUnit.MINUTES), Duration.of(5, ChronoUnit.MINUTES), 5),        // Computed timeout is greater than maxPullTimeout
                Arguments.of(3000, Duration.of(10, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 10),     // Computed timeout is less than minPullTimeout
                Arguments.of(3000, Duration.of(10, ChronoUnit.MINUTES), Duration.of(5, ChronoUnit.MINUTES), 5)        // Limits are reversed; maxPullTimeout is the one selected
        );
    }

    @ParameterizedTest
    @MethodSource("computeImagePullTimeoutValues")
    void computeImagePullTimeout(long maxExecutionTime,
                                 Duration minPullTimeout,
                                 Duration maxPullTimeout,
                                 long expectedTimeout) {
        final TaskDescription taskDescription = TaskDescription
                .builder()
                .maxExecutionTime(maxExecutionTime)
                .build();

        when(dockerRegistryConfiguration.getMinPullTimeout()).thenReturn(minPullTimeout);
        when(dockerRegistryConfiguration.getMaxPullTimeout()).thenReturn(maxPullTimeout);

        Assertions.assertThat(computeManagerService.computeImagePullTimeout(taskDescription))
                .isEqualTo(expectedTimeout);
    }
}