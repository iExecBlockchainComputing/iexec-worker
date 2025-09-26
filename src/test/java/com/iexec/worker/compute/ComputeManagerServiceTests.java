/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.DealParams;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComputeManagerServiceTests {

    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private static final String DATASET_URI = "DATASET_URI";
    private static final String DIGEST = "digest";
    private static final String APP_URI = "APP_URI";
    private static final TeeSessionGenerationResponse SECURE_SESSION = mock(TeeSessionGenerationResponse.class);
    private static final long MAX_EXECUTION_TIME = 1000;

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

    private TaskDescription.TaskDescriptionBuilder createTaskDescriptionBuilder(boolean isTeeTask) {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(List.of("file0", "file1"))
                .build();
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri(APP_URI)
                .datasetUri(DATASET_URI)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .dealParams(dealParams)
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
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(true);
        assertThat(computeManagerService.downloadApp(taskDescription)).isTrue();
    }

    @Test
    void shouldNotDownloadAppSincePullImageFailed() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerService.getClient(taskDescription.getAppUri())).thenReturn(dockerClient);
        when(dockerClient.pullImage(taskDescription.getAppUri(), Duration.ofMinutes(0))).thenReturn(false);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(false);
        assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceNoTaskDescription() {
        assertThat(computeManagerService.downloadApp(null)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceNoAppType() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false)
                .appType(null)
                .build();
        assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldNotDownloadAppSinceWrongAppType() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false)
                .appType(DappType.BINARY)
                .build();
        assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    void shouldHaveImageDownloaded() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(true);
        assertThat(computeManagerService.isAppDownloaded(APP_URI)).isTrue();
    }

    @Test
    void shouldNotHaveImageDownloaded() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        when(dockerService.getClient()).thenReturn(dockerClient);
        when(dockerClient.isImagePresent(taskDescription.getAppUri())).thenReturn(false);
        assertThat(computeManagerService.isAppDownloaded(APP_URI)).isFalse();
    }
    //endregion

    //region runPreCompute
    @Test
    void shouldRunStandardPreCompute() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);

        assertThat(preComputeResponse.isSuccessful()).isTrue();
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
        assertThat(preComputeResponse).isEqualTo(mockResponse);
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
                .exitCauses(List.of(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING))
                .build());

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        assertThat(preComputeResponse.getSecureSession()).isNull();
        assertThat(preComputeResponse.isSuccessful()).isFalse();
        assertThat(preComputeResponse.getExitCauses())
                .containsExactly(ReplicateStatusCause.PRE_COMPUTE_DATASET_URL_MISSING);
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
        assertThat(appComputeResponse.isSuccessful()).isTrue();
        assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(appComputeService, times(1))
                .runCompute(taskDescription, null);
    }

    @Test
    void shouldRunStandardComputeWithFailureResponse() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        AppComputeResponse expectedDockerRunResponse =
                AppComputeResponse.builder()
                        .exitCauses(List.of(ReplicateStatusCause.APP_COMPUTE_FAILED))
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, null))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, null);
        assertThat(appComputeResponse.isSuccessful()).isFalse();
        assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(appComputeResponse.getStderr()).isEqualTo(
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
        assertThat(appComputeResponse.isSuccessful()).isTrue();
        assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(appComputeResponse.getStderr()).isEqualTo(
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
                        .exitCauses(List.of(ReplicateStatusCause.APP_COMPUTE_FAILED))
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(appComputeService.runCompute(taskDescription, SECURE_SESSION))
                .thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription,
                        SECURE_SESSION);
        assertThat(appComputeResponse.isSuccessful()).isFalse();
        assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(appComputeResponse.getStderr()).isEqualTo(
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
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCauses()).containsExactly(ReplicateStatusCause.POST_COMPUTE_COMPUTED_FILE_NOT_FOUND);
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
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCauses()).containsExactly(ReplicateStatusCause.POST_COMPUTE_RESULT_DIGEST_COMPUTATION_FAILED);
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
        assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(postComputeService).runStandardPostCompute(taskDescription);
        verify(resultService).readComputedFile(CHAIN_TASK_ID);
        verify(resultService).computeResultDigest(computedFile);
        verify(resultService).saveResultInfo(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = ReplicateStatusCause.class, names = "POST_COMPUTE_.*", mode = EnumSource.Mode.MATCH_ALL)
    void shouldRunStandardPostComputeWithFailureResponse(ReplicateStatusCause statusCause) {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(false).build();
        PostComputeResponse postComputeResponse = PostComputeResponse.builder().exitCauses(List.of(statusCause)).build();
        when(postComputeService.runStandardPostCompute(taskDescription)).thenReturn(postComputeResponse);

        postComputeResponse = computeManagerService.runPostCompute(taskDescription, null);
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getExitCauses()).containsExactly(statusCause);
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
        assertThat(postComputeResponse.isSuccessful()).isTrue();
        assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(postComputeResponse.getStderr()).isEqualTo(
                "stderr");
        verify(postComputeService).runTeePostCompute(taskDescription, SECURE_SESSION);
        verify(resultService).readComputedFile(CHAIN_TASK_ID);
        verify(resultService).computeResultDigest(computedFile);
        verify(resultService).saveResultInfo(any(), any());
    }

    @Test
    void shouldRunTeePostComputeWithFailureResponse() {
        final TaskDescription taskDescription = createTaskDescriptionBuilder(true).build();
        PostComputeResponse expectedDockerRunResponse =
                PostComputeResponse.builder()
                        .exitCauses(List.of(ReplicateStatusCause.APP_COMPUTE_FAILED))
                        .stdout(dockerLogs.getStdout())
                        .stderr(dockerLogs.getStderr())
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION))
                .thenReturn(expectedDockerRunResponse);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION);
        assertThat(postComputeResponse.isSuccessful()).isFalse();
        assertThat(postComputeResponse.getStdout()).isEqualTo(
                "stdout");
        assertThat(postComputeResponse.getStderr()).isEqualTo(
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

        assertThat(computeManagerService.computeImagePullTimeout(taskDescription))
                .isEqualTo(expectedTimeout);
    }
    //endregion

    // region abort
    @Test
    void shouldNotAbortWhenContainersAreStillRunning() {
        when(dockerService.stopRunningContainersWithNameContaining(any())).thenReturn(1L);
        assertThat(computeManagerService.abort(CHAIN_TASK_ID)).isFalse();
    }

    @Test
    void shouldAbortTask() {
        when(dockerService.stopRunningContainersWithNameContaining(any())).thenReturn(0L);
        assertThat(computeManagerService.abort(CHAIN_TASK_ID)).isTrue();
    }
    // endregion
}
