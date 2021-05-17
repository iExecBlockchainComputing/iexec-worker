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

package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.contribution.Contribution;
import com.iexec.common.dapp.DappType;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import com.iexec.worker.utils.WorkflowException;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


public class TaskManagerServiceTests {

    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    public static final String PATH_TO_DOWNLOADED_FILE = "/path/to/downloaded/file";

    @InjectMocks
    private TaskManagerService taskManagerService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ContributionService contributionService;
    @Mock
    private RevealService revealService;
    @Mock
    private ComputeManagerService computeManagerService;
    @Mock
    private SconeTeeService sconeTeeService;
    @Mock
    private DataService dataService;
    @Mock
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    TaskDescription getStubTaskDescription(boolean isTeeTask) {
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri("appUri")
                .datasetAddress("datasetAddress")
                .datasetName("datasetName")
                .datasetChecksum("datasetChecksum")
                .datasetUri("datasetUri")
                .isTeeTask(isTeeTask)
                .inputFiles(List.of("http://file1"))
                .build();
    }

    WorkerpoolAuthorization getStubAuth(String enclaveChallenge) {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
    }

    @Test
    public void shouldStart() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(getStubTaskDescription(false));
        when(sconeTeeService.isTeeEnabled()).thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotStartSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotStartSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(TASK_DESCRIPTION_NOT_FOUND);
    }

    @Test
    public void shouldNotStartSinceTeeTaskAndButEnabledOnHost() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(getStubTaskDescription(true));
        when(sconeTeeService.isTeeEnabled()).thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(TEE_NOT_SUPPORTED);
    }


    @Test
    public void shouldDownloadApp() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(true);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotDownloadAppSinceCannotContributionStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotDownloadAppSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(TASK_DESCRIPTION_NOT_FOUND);
    }

    @Test
    public void shouldAppDownloadFailedAndTriggerPostComputeHookWithSuccess() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(APP_IMAGE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure1() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(POST_COMPUTE_FAILED);
    }

    @Test
    public void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure2() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(false).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(POST_COMPUTE_FAILED);
    }

    // downloadData()

    /**
     * Note : Remember dataset URI and input files are optional
     */

    @Test
    public void shouldNotDownloadDataSinceCannotContributeStatusIsPresent() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    // without dataset + without input files

    @Test
    public void shouldReturnSuccessAndNotDownloadDataSinceEmptyUrls() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(null);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService, never()).downloadStandardDataset(taskDescription);
        verify(dataService, never()).downloadStandardInputFiles(anyString(), anyList());
    }

    // with dataset + with input files

    @Test
    public void shouldDownloadDatasetAndInputFiles() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenReturn(PATH_TO_DOWNLOADED_FILE);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService).downloadStandardDataset(taskDescription);
        verify(dataService).downloadStandardInputFiles(anyString(), anyList());
    }

    // with dataset + without input files

    @Test
    public void shouldDownloadDatasetAndNotInputFiles() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setInputFiles(null);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenReturn(PATH_TO_DOWNLOADED_FILE);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService).downloadStandardDataset(taskDescription);
        verify(dataService, never()).downloadStandardInputFiles(anyString(), anyList());
    }

    // without dataset + with input files

    @Test
    public void shouldDownloadInputFilesAndNotDataset() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetAddress("");
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenReturn(PATH_TO_DOWNLOADED_FILE);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService, never()).downloadStandardDataset(taskDescription);
        verify(dataService).downloadStandardInputFiles(anyString(), anyList());
    }

    // with dataset + with input files + TEE task

    @Test
    public void shouldNotDownloadDataWithDatasetUriForTeeTaskAndReturnSuccess()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadStandardDataset(taskDescription))
                .thenReturn(PATH_TO_DOWNLOADED_FILE);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService, never()).downloadStandardDataset(taskDescription);
        verify(dataService, never()).downloadStandardInputFiles(anyString(), anyList());
    }    

    // DATASET_FILE_DOWNLOAD_FAILED exception

    @Test
    public void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithSuccess()
                throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithFailure1()
                throws Exception{
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED);
    }

    @Test
    public void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithFailure2()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(false).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED);
    }

    // with dataset and on-chain checksum

    @Test
    public void shouldWithDatasetUriAndChecksumDownloadData() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenReturn(PATH_TO_DOWNLOADED_FILE);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    // DATASET_FILE_BAD_CHECKSUM exception

    @Test
    public void shouldHandleWorflowExceptionInDownloadDataAndTriggerPostComputeHookWithSuccess()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_BAD_CHECKSUM));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(DATASET_FILE_BAD_CHECKSUM);
        verify(dataService).downloadStandardDataset(taskDescription);
    }

    // with input files

    @Test
    public void shouldWithInputFilesDownloadData() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        doNothing().when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles());
    }

    @Test
    public void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithSuccess()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles());
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(INPUT_FILES_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithFailure1()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles());
                when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED);
    }

    @Test
    public void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithFailure2()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles());
                when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, ""))
                .thenReturn(PostComputeResponse.builder().isSuccessful(false).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED);
    }

    @Test
    public void shouldCompute() {
        TaskDescription taskDescription = TaskDescription.builder().build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        ComputedFile computedFile1 = ComputedFile.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(computeManagerService.runPostCompute(any(), any()))
                .thenReturn(PostComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile1);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        // pre-compute + app-compute + post-compute stdout
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .successWithStdout("stdout\nstdout\nstdout"));
    }

    @Test
    public void shouldNotComputeSinceCannotContributeStatusIsPresent() {
        ReplicateStatusCause replicateStatusCause =
                ReplicateStatusCause.CONTRIBUTION_AUTHORIZATION_NOT_FOUND;

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(replicateStatusCause));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(replicateStatusCause));
    }

    @Test
    public void shouldNotComputeSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(TASK_DESCRIPTION_NOT_FOUND));
    }

    @Test
    public void shouldNotComputeSinceAppNotDownloaded() {
        TaskDescription taskDescription = TaskDescription.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(APP_NOT_FOUND_LOCALLY));
    }

    @Test
    public void shouldNotComputeSinceFailedPreCompute() {
        TaskDescription taskDescription = TaskDescription.builder().build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().isSuccessful(false).stdout("stdout").build());

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(PRE_COMPUTE_FAILED));
    }

    @Test
    public void shouldNotComputeSinceFailedAppCompute() {
        TaskDescription taskDescription = TaskDescription.builder().build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().isSuccessful(false).stdout("stdout").build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failureWithStdout("stdout"));
    }

    @Test
    public void shouldNotComputeSinceFailedPostCompute() {
        TaskDescription taskDescription = TaskDescription.builder().build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().isSuccessful(true).stdout("stdout").build());
        when(computeManagerService.runPostCompute(any(), any()))
                .thenReturn(PostComputeResponse.builder().isSuccessful(false).stdout("stdout").build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failureWithStdout(POST_COMPUTE_FAILED, "stdout"));
    }

    @Test
    public void shouldContribute() {
        ComputedFile computedFile = mock(ComputedFile.class);
        Contribution contribution = mock(Contribution.class);
        ChainReceipt chainReceipt =
                ChainReceipt.builder().blockNumber(10).build();
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(contributionService.getContribution(computedFile))
                .thenReturn(contribution);
        when(contributionService.contribute(contribution))
                .thenReturn(Optional.of(chainReceipt));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .success(chainReceipt));
    }

    @Test
    public void shouldNotContributeSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(CONTRIBUTION_TIMEOUT));
    }

    @Test
    public void shouldNotContributeSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(TASK_DESCRIPTION_NOT_FOUND));
    }

    @Test
    public void shouldNotContributeSinceNotEnoughGas() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(OUT_OF_GAS));
    }

    @Test
    public void shouldNotContributeSinceNoComputedFile() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(DETERMINISM_HASH_NOT_FOUND));
    }

    @Test
    public void shouldNotContributeSinceNoContribution() {
        ComputedFile computedFile = mock(ComputedFile.class);
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(contributionService.getContribution(computedFile))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse
                        .failure(ENCLAVE_SIGNATURE_NOT_FOUND));
    }

    @Test
    public void shouldNotContributeSinceFailedToContribute() {
        ComputedFile computedFile = mock(ComputedFile.class);
        Contribution contribution = mock(Contribution.class);
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(contributionService.getContribution(computedFile))
                .thenReturn(contribution);
        when(contributionService.contribute(contribution))
                .thenReturn(Optional.empty());

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }


    @Test
    public void shouldNotContributeSinceInvalidContributeChainReceipt() {
        ComputedFile computedFile = mock(ComputedFile.class);
        Contribution contribution = mock(Contribution.class);
        TaskDescription taskDescription = getStubTaskDescription(false);
        ChainReceipt chainReceipt =
                ChainReceipt.builder().blockNumber(0).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(contributionService.getContribution(computedFile))
                .thenReturn(contribution);
        when(contributionService.contribute(contribution))
                .thenReturn(Optional.of(chainReceipt));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    public void shouldReveal() {
        long consensusBlock = 20;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();
        String resultDigest = "resultDigest";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        ChainReceipt chainReceipt =
                ChainReceipt.builder().blockNumber(10).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock))
                .thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, computedFile.getResultDigest()))
                .thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(revealService.reveal(CHAIN_TASK_ID, resultDigest))
                .thenReturn(Optional.of(chainReceipt));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.success(chainReceipt));
    }

    @Test
    public void shouldNotRevealSinceNoConsensusBlock() {
        long consensusBlock = 0;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CONSENSUS_BLOCK_MISSING));
    }

    @Test
    public void shouldNotRevealSinceNoExtraForRetrievingConsensusBlock() {
        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, null);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CONSENSUS_BLOCK_MISSING));
    }

    @Test
    public void shouldNotRevealSinceEmptyResultDigest() {
        long consensusBlock = 20;
        String resultDigest = "";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID,
                        TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND));
    }

    @Test
    public void shouldNotRevealSinceConsensusBlockNotReached() {
        long consensusBlock = 20;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();
        String resultDigest = "resultDigest";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(BLOCK_NOT_REACHED));
    }

    @Test
    public void shouldNotRevealSinceCannotReveal() {
        long consensusBlock = 20;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();
        String resultDigest = "resultDigest";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock))
                .thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, computedFile.getResultDigest()))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CANNOT_REVEAL));
    }


    @Test
    public void shouldNotRevealSinceFailedToReveal() {
        long consensusBlock = 20;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();
        String resultDigest = "resultDigest";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock))
                .thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, computedFile.getResultDigest()))
                .thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(revealService.reveal(CHAIN_TASK_ID, resultDigest))
                .thenReturn(Optional.empty());

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    public void shouldNotRevealSinceInvalidRevealChainReceipt() {
        long consensusBlock = 20;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();
        String resultDigest = "resultDigest";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        ChainReceipt chainReceipt =
                ChainReceipt.builder().blockNumber(0).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock))
                .thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, computedFile.getResultDigest()))
                .thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(revealService.reveal(CHAIN_TASK_ID, resultDigest))
                .thenReturn(Optional.of(chainReceipt));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    public void shouldUploadResultWithResultUri() {
        String resultUri = "resultUri";
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn(resultUri);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.success(resultUri, ""));
    }

    @Test
    public void shouldUploadResultWithResultUriAndCallbackData() {
        String resultUri = "resultUri";
        String callbackData = "callbackData";
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn(resultUri);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(ComputedFile.builder()
                        .callbackData(callbackData)
                        .build()
                );

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.success(resultUri, callbackData));
    }

    @Test
    public void shouldNotUploadResultSinceEmptyResultLink() {
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn("");

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure(RESULT_LINK_MISSING));
    }

    @Test
    public void shouldComplete() {
        when(resultService.removeResult(CHAIN_TASK_ID)).thenReturn(true);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.complete(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.success());
    }

    @Test
    public void shouldNotCompleteSinceCannotRemoveResult() {
        when(resultService.removeResult(CHAIN_TASK_ID)).thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.complete(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse).isNotNull();
        Assertions.assertThat(replicateActionResponse).isEqualTo(
                ReplicateActionResponse.failure());
    }

    @Test
    public void shouldAbort() {
        when(resultService.removeResult(CHAIN_TASK_ID)).thenReturn(true);

        boolean isAborted = taskManagerService.abort(CHAIN_TASK_ID);

        Assertions.assertThat(isAborted).isTrue();
        verify(resultService, times(1))
                .removeResult(CHAIN_TASK_ID);
    }

    //TODO clean theses
    //misc

    @Test
    public void shouldFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        assertThat(taskManagerService.hasEnoughGas()).isTrue();
    }

    @Test
    public void shouldNotFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        assertThat(taskManagerService.hasEnoughGas()).isFalse();
    }

    @Test
    public void shouldFindChainReceiptValid() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(5,
                "txHash"));

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isTrue();
    }

    @Test
    public void shouldFindChainReceiptNotValidSinceBlockIsZero() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(0,
                "txHash"));

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isFalse();
    }
}