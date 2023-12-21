/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.containers.DockerLogs;
import com.iexec.commons.containers.client.DockerClientInstance;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.dapp.DappType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationResponse;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ComputeManagerServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String DIGEST = "digest";
    private final static String APP_URI = "APP_URI";
    private final static String TEE_POST_COMPUTE_IMAGE = "TEE_POST_COMPUTE_IMAGE";
    private final static TeeSessionGenerationResponse SECURE_SESSION = mock(TeeSessionGenerationResponse.class);
    private final static long MAX_EXECUTION_TIME = 1000;

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
    private ResultService resultService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    private TaskDescription.TaskDescriptionBuilder createTaskDescriptionBuilder(boolean isTeeTask) {
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri(APP_URI)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .inputFiles(Arrays.asList("file0", "file1"))
                .isTeeTask(isTeeTask)
                .maxExecutionTime(3000);
    }

    //region downloadApp
    @Test
    void shouldDownloadApp() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerRegistryConfiguration.getMinPullTimeout()).thenReturn(Duration.of(5, ChronoUnit.MINUTES));
        when(dockerRegistryConfiguration.getMaxPullTimeout()).thenReturn(Duration.of(30, ChronoUnit.MINUTES));
        when(dockerService.getClient(taskDescription.getAppUri())).thenReturn(dockerClient);
        when(dockerClient.pullImage(taskDescription.getAppUri(), Duration.of(7, ChronoUnit.MINUTES))).thenReturn(true);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isTrue();
    }

    @Test
    void shouldNotDownloadAppSincePullImageFailed() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
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
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false)
                .appType(null)
                .build();
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceWrongAppType() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false)
                .appType(DappType.BINARY)
                .build();
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldHaveImageDownloaded() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(true);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isTrue();
    }

    @Test
    void shouldNotHaveImageDownloaded() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(false);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isFalse();
    }
    //endregion

    //region runPreCompute
    @Test
    void shouldRunStandardPreCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);

        Assertions.assertThat(preComputeResponse.isSuccessful()).isTrue();
    }

    @Test
    void shouldRunTeePreCompute() {
        PreComputeResponse mockResponse = mock(PreComputeResponse.class);
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(preComputeService.runTeePreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn(mockResponse);

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse).isEqualTo(mockResponse);
        verify(preComputeService, times(1))
                .runTeePreCompute(taskDescription,
                        workerpoolAuthorization);
    }

    @Test
    void shouldRunTeePreComputeWithFailureResponse() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(preComputeService.runTeePreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn(PreComputeResponse.builder()
                .secureSession(null)
                .exitCause(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING)
                .build());

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse.getSecureSession()).isNull();
        Assertions.assertThat(preComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(preComputeResponse.getExitCause())
                .isEqualTo(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
    }
    //endregion

    //region runCompute
    @Test
    void shouldRunStandardCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, null))
                .thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.getAbsolutePath());

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, null);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(appComputeService, times(1))
                .runCompute(taskDescription, null);
    }

    @Test
    void shouldRunStandardComputeWithFailureResponse() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .exitCause(ReplicateStatusCause.APP_COMPUTE_FAILED)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, null))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, null);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }

    @Test
    void shouldRunTeeCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription,
                SECURE_SESSION)).thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.getAbsolutePath());

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription,
                        SECURE_SESSION);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(appComputeService, times(1))
                .runCompute(taskDescription,
                        SECURE_SESSION);
    }

    @Test
    void shouldRunTeeComputeWithFailure() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .exitCause(ReplicateStatusCause.APP_COMPUTE_FAILED)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, SECURE_SESSION))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription,
                        SECURE_SESSION);
        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }
    //endregion

    //region runPostCompute
    @Test
    void shouldNotBeSuccessfulWhenComputedFileNotFound() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(PostComputeResponse.builder().build());
        when(resultService.readComputedFile(CHAIN_TASK_ID)).thenReturn(null);
        PostComputeResponse postComputeResponse = computeManagerService.runPostCompute(taskDescription, null);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(postComputeResponse.getExitCause()).isEqualTo(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND);
    }

    @Test
    void shouldNotBeSuccessfulWhenResultDigestComputationFails() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(PostComputeResponse.builder().build());
        ComputedFile computedFile = ComputedFile.builder().build();
        when(resultService.readComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(resultService.computeResultDigest(computedFile)).thenReturn("");
        PostComputeResponse postComputeResponse = computeManagerService.runPostCompute(taskDescription, null);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(postComputeResponse.getExitCause()).isEqualTo(ReplicateStatusCause.POST_COMPUTE_RESULT_DIGEST_COMPUTATION_FAILED);
    }

    @Test
    void shouldRunStandardPostCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(PostComputeResponse.builder().build());
        ComputedFile computedFile = mock(ComputedFile.class);
        when(resultService.readComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(resultService.computeResultDigest(computedFile)).thenReturn(DIGEST);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription, null);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(postComputeService).runStandardPostCompute(taskDescription);
        verify(resultService).readComputedFile(CHAIN_TASK_ID);
        verify(resultService).computeResultDigest(computedFile);
        verify(resultService).saveResultInfo(anyString(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ReplicateStatusCause.class, names = "POST_COMPUTE_.*", mode = EnumSource.Mode.MATCH_ALL)
    void shouldRunStandardPostComputeWithFailureResponse(ReplicateStatusCause statusCause) {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        PostComputeResponse postComputeResponse = PostComputeResponse.builder().exitCause(statusCause).build();
        when(postComputeService.runStandardPostCompute(taskDescription)).thenReturn(postComputeResponse);

        postComputeResponse = computeManagerService.runPostCompute(taskDescription, null);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(postComputeResponse.getExitCause()).isEqualTo(statusCause);
    }

    @Test
    void shouldRunTeePostCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        PostComputeResponse expectedDockerRunResponse =
                PostComputeResponse.builder()
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION))
                .thenReturn(expectedDockerRunResponse);
        ComputedFile computedFile = mock(ComputedFile.class);
        when(resultService.readComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(resultService.computeResultDigest(computedFile)).thenReturn(DIGEST);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isTrue();
        Assertions.assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(postComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(postComputeService).runTeePostCompute(taskDescription, SECURE_SESSION);
        verify(resultService).readComputedFile(CHAIN_TASK_ID);
        verify(resultService).computeResultDigest(computedFile);
        verify(resultService).saveResultInfo(anyString(), any(), any());
    }

    @Test
    void shouldRunTeePostComputeWithFailureResponse() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        PostComputeResponse expectedDockerRunResponse =
                PostComputeResponse.builder()
                        .exitCause(ReplicateStatusCause.APP_COMPUTE_FAILED)
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION))
                .thenReturn(expectedDockerRunResponse);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(postComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }
    //endregion

    //region computeImagePullTimeout
    static Stream<Arguments> computeImagePullTimeoutValues() {
        return Stream.of(
                // maxExecutionTime, minPullTimeout, maxPullTimeout, expectedTimeout

                // Default values
                Arguments.of(3000, Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 7),        // XS category
                Arguments.of(12000, Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 13),      // S category
                Arguments.of(36000, Duration.of(5, ChronoUnit.MINUTES), Duration.of(30, ChronoUnit.MINUTES), 18),      // M category
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
    //endregion
}
