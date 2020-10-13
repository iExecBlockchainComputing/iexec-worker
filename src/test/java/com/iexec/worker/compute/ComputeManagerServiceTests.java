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
import com.iexec.worker.docker.DockerLogs;
import com.iexec.worker.docker.DockerRunResponse;
import com.iexec.worker.docker.DockerService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.Mockito.*;

public class ComputeManagerServiceTests {

    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private final static String DATASET_URI = "DATASET_URI";
    private final static String APP_URI = "APP_URI";
    private final static String TEE_POST_COMPUTE_IMAGE =
            "TEE_POST_COMPUTE_IMAGE";
    private final static String SECURE_SESSION_ID = "SECURE_SESSION_ID";
    private final static long MAX_EXECUTION_TIME = 1000;
    private static final String IEXEC_WORKER_TMP_FOLDER = "./src/test" +
            "/resources/tmp/test-worker";

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
            .build();
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .build();
    private final DockerLogs dockerLogs =
            DockerLogs.builder().stdout("stdout").stderr("stderr").build();

    @Rule
    public TemporaryFolder jUnitTemporaryFolder = new TemporaryFolder();

    @InjectMocks
    private ComputeManagerService computeManagerService;
    @Mock
    private DockerService dockerService;
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

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

    }

    @Test
    public void shouldDownloadApp() {
        when(dockerService.pullImage(taskDescription.getAppUri())).thenReturn(true);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isTrue();
    }

    @Test
    public void shouldNotDownloadAppSincePullImageFailed() {
        when(dockerService.pullImage(taskDescription.getAppUri())).thenReturn(false);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    public void shouldNotDownloadAppSinceNoTaskDescription() {
        Assertions.assertThat(computeManagerService.downloadApp(null)).isFalse();
    }

    @Test
    public void shouldNotDownloadAppSinceNoAppType() {
        taskDescription.setAppType(null);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    public void shouldNotDownloadAppSinceWrongAppType() {
        taskDescription.setAppType(DappType.BINARY);
        Assertions.assertThat(computeManagerService.downloadApp(taskDescription)).isFalse();
    }

    @Test
    public void shouldHaveImageDownloaded() {
        when(dockerService.isImagePulled(taskDescription.getAppUri())).thenReturn(true);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isTrue();
    }

    @Test
    public void shouldNotHaveImageDownloaded() {
        when(dockerService.isImagePulled(taskDescription.getAppUri())).thenReturn(false);
        Assertions.assertThat(computeManagerService.isAppDownloaded(APP_URI)).isFalse();
    }

    // pre compute

    @Test
    public void shouldRunStandardPreCompute() {
        taskDescription.setTeeTask(false);
        when(preComputeService.runStandardPreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn(true);

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);

        Assertions.assertThat(preComputeResponse.isSuccessful()).isTrue();
        verify(preComputeService, times(1))
                .runStandardPreCompute(taskDescription,
                        workerpoolAuthorization);
    }

    @Test
    public void shouldRunStandardPreComputeWithFailureResponse() {
        taskDescription.setTeeTask(false);
        when(preComputeService.runStandardPreCompute(taskDescription,
                workerpoolAuthorization)).thenReturn(false);

        PreComputeResponse preComputeResponse =
                computeManagerService.runPreCompute(taskDescription,
                        workerpoolAuthorization);
        Assertions.assertThat(preComputeResponse.isSuccessful()).isFalse();
    }

    @Test
    public void shouldRunTeePreCompute() {
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
    public void shouldRunTeePreComputeWithFailureResponse() {
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
    public void shouldRunStandardCompute() throws IOException {
        taskDescription.setTeeTask(false);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(true)
                        .dockerLogs(dockerLogs)
                        .build();
        when(appComputeService.runCompute(taskDescription,
                "")).thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.newFolder().getAbsolutePath());

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
    public void shouldRunStandardComputeWithFailureResponse() {
        taskDescription.setTeeTask(false);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(false)
                        .dockerLogs(dockerLogs)
                        .build();
        when(appComputeService.runCompute(taskDescription,
                "")).thenReturn(expectedDockerRunResponse);

        AppComputeResponse appComputeResponse =
                computeManagerService.runCompute(taskDescription, "");
        Assertions.assertThat(appComputeResponse.isSuccessful()).isFalse();
        Assertions.assertThat(appComputeResponse.getStdout()).isEqualTo(
                "stdout");
        Assertions.assertThat(appComputeResponse.getStderr()).isEqualTo(
                "stderr");
    }

    @Test
    public void shouldRunTeeCompute() throws IOException {
        taskDescription.setTeeTask(true);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(true)
                        .dockerLogs(dockerLogs)
                        .build();
        when(appComputeService.runCompute(taskDescription,
                SECURE_SESSION_ID)).thenReturn(expectedDockerRunResponse);
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(jUnitTemporaryFolder.newFolder().getAbsolutePath());

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
    public void shouldRunTeeComputeWithFailure() {
        taskDescription.setTeeTask(true);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(false)
                        .dockerLogs(dockerLogs)
                        .build();
        when(appComputeService.runCompute(taskDescription, SECURE_SESSION_ID)).thenReturn(expectedDockerRunResponse);

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
    public void shouldRunStandardPostCompute() {
        taskDescription.setTeeTask(false);
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(true);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription, "");
        Assertions.assertThat(postComputeResponse.isSuccessful()).isTrue();
        verify(postComputeService, times(1))
                .runStandardPostCompute(taskDescription);
    }

    @Test
    public void shouldRunStandardPostComputeWithFailureResponse() {
        taskDescription.setTeeTask(false);
        when(postComputeService.runStandardPostCompute(taskDescription))
                .thenReturn(false);

        PostComputeResponse postComputeResponse =
                computeManagerService.runPostCompute(taskDescription,
                        SECURE_SESSION_ID);
        Assertions.assertThat(postComputeResponse.isSuccessful()).isFalse();
    }

    @Test
    public void shouldRunTeePostCompute() {
        taskDescription.setTeeTask(true);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(true)
                        .dockerLogs(dockerLogs)
                        .build();
        when(postComputeService.runTeePostCompute(taskDescription,
                SECURE_SESSION_ID))
                .thenReturn(expectedDockerRunResponse);

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
    }

    @Test
    public void shouldRunTeePostComputeWithFailureResponse() {
        taskDescription.setTeeTask(true);
        DockerRunResponse expectedDockerRunResponse =
                DockerRunResponse.builder()
                        .isSuccessful(false)
                        .dockerLogs(dockerLogs)
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

    // get computed file

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFile() {
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId +
                        "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile =
                computeManagerService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0x09b727883db89fa3b3504f83e0c67d04a0d4fc35a9670cc4517c49d2a27ad171");
    }

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFileTree() {
        String chainTaskId = "deterministic-output-directory";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId +
                        "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile =
                computeManagerService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        System.out.println(hash);
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0xc6114778cc5c33db5fbbd4d0f9be116ed0232961045341714aba5a72d3ef7402");
    }

    @Test
    public void shouldGetComputedFileWithWeb3ResultDigest() {
        String chainTaskId = "callback-directory";

        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().isCallbackRequested(true).build());

        ComputedFile computedFile =
                computeManagerService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        System.out.println(hash);
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6");
    }

}