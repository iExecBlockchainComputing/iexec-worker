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
import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ComputeLogs;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
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
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.pubsub.SubscriptionService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.utils.WorkflowException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class TaskManagerServiceTests {

    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    private static final String WORKER_ADDRESS = "WORKER_ADDRESS";
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
    private TeeServicesManager teeServicesManager;
    @Mock
    private DataService dataService;
    @Mock
    private ResultService resultService;
    @Mock
    private DockerService dockerService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private PurgeService purgeService;

    @Mock
    private TeeService teeMockedService;

    @Captor
    private ArgumentCaptor<Predicate<String>> predicateCaptor;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
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

    //region start
    @Test
    void shouldStart() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(getStubTaskDescription(false));
        when(teeMockedService.isTeeEnabled()).thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    void shouldNotStartSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldNotStartSinceNoTaskDescription() {
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
    void shouldNotStartSinceStandardTaskWithEncryption() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        TaskDescription taskDescription = TaskDescription.builder().isResultEncryption(true).build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        ReplicateActionResponse actionResponse = taskManagerService.start(CHAIN_TASK_ID);
        Assertions.assertThat(actionResponse.isSuccess()).isFalse();
        Assertions.assertThat(actionResponse.getDetails().getCause()).isEqualTo(TASK_DESCRIPTION_INVALID);
    }

    @Test
    void shouldStartWithTee() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(getStubTaskDescription(true));
        when(teeMockedService.isTeeEnabled()).thenReturn(true);
        when(teeMockedService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    void shouldNotStartSinceTeePrerequisitesAreNotMet() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID))
                .thenReturn(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(getStubTaskDescription(true));
        when(teeMockedService.isTeeEnabled()).thenReturn(true);
        when(teeMockedService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID)).thenReturn(Optional.of(TEE_NOT_SUPPORTED));

        ReplicateActionResponse actionResponse =
                taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(TEE_NOT_SUPPORTED);
    }
    //endregion

    //region downloadApp
    @Test
    void shouldDownloadApp() {
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
    void shouldNotDownloadAppSinceCannotContributionStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldNotDownloadAppSinceNoTaskDescription() {
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
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithSuccess() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(APP_IMAGE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure1() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    @Test
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure2() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().exitCause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }
    //endregion

    //region downloadData

    /**
     * Note : Remember dataset URI and input files are optional
     */

    @Test
    void shouldNotDownloadDataSinceCannotContributeStatusIsPresent() {
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
    void shouldReturnSuccessAndNotDownloadDataSinceEmptyUrls() throws Exception {
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
    void shouldDownloadDatasetAndInputFiles() throws Exception {
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
    void shouldDownloadDatasetAndNotInputFiles() throws Exception {
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
    void shouldDownloadInputFilesAndNotDataset() throws Exception {
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
    void shouldNotDownloadDataWithDatasetUriForTeeTaskAndReturnSuccess()
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
    void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithSuccess()
                throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithFailure1()
                throws Exception{
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    @Test
    void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithFailure2()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().exitCause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    // with dataset and on-chain checksum

    @Test
    void shouldWithDatasetUriAndChecksumDownloadData() throws Exception {
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
    void shouldHandleWorkflowExceptionInDownloadDataAndTriggerPostComputeHookWithSuccess()
            throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_BAD_CHECKSUM));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(DATASET_FILE_BAD_CHECKSUM);
        verify(dataService).downloadStandardDataset(taskDescription);
    }

    // with input files

    @Test
    void shouldWithInputFilesDownloadData() throws Exception {
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
    void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithSuccess()
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
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(INPUT_FILES_DOWNLOAD_FAILED);
    }

    @Test
    void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithFailure1()
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
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    @Test
    void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithFailure2()
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
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().exitCause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }
    //endregion

    //region compute
    @Test
    void shouldComputeStandardTask() {
        TaskDescription taskDescription = TaskDescription.builder()
                .isTeeTask(false)
                .build();
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
                .thenReturn(PreComputeResponse.builder().build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().stdout("stdout").stderr("stderr").build());
        when(computeManagerService.runPostCompute(any(), any()))
                .thenReturn(PostComputeResponse.builder().build());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile1);
        when(workerConfigurationService.getWorkerWalletAddress()).thenReturn(WORKER_ADDRESS);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(
                        ReplicateActionResponse
                                .successWithLogs(ComputeLogs.builder()
                                        .stdout("stdout")
                                        .stderr("stderr")
                                        .build()));
    }
    @Test
    void shouldCompute() {
        TaskDescription taskDescription = TaskDescription.builder()
                .isTeeTask(true)
                .build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        ComputedFile computedFile1 = ComputedFile.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(teeMockedService.prepareTeeForTask(CHAIN_TASK_ID))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().stdout("stdout").stderr("stderr").build());
        when(computeManagerService.runPostCompute(any(), any()))
                .thenReturn(PostComputeResponse.builder().build());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile1);
        when(workerConfigurationService.getWorkerWalletAddress()).thenReturn(WORKER_ADDRESS);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(
                        ReplicateActionResponse
                                .successWithLogs(ComputeLogs.builder()
                                        .stdout("stdout")
                                        .stderr("stderr")
                                        .build()));
    }

    @ParameterizedTest
    @EnumSource(value = ReplicateStatusCause.class,
            names = {"CHAIN_UNREACHABLE", "STAKE_TOO_LOW", "TASK_NOT_ACTIVE", "CONTRIBUTION_TIMEOUT",
                    "CONTRIBUTION_ALREADY_SET", "WORKERPOOL_AUTHORIZATION_NOT_FOUND"})
    void shouldNotComputeSinceCannotContributeStatusIsPresent(ReplicateStatusCause replicateStatusCause) {

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(replicateStatusCause));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(replicateStatusCause));
    }

    @Test
    void shouldNotComputeSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(TASK_DESCRIPTION_NOT_FOUND));
    }

    @Test
    void shouldNotComputeSinceAppNotDownloaded() {
        TaskDescription taskDescription = TaskDescription.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(APP_NOT_FOUND_LOCALLY));
    }

    @Test
    void shouldNotComputeSinceFailedPreCompute() {
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
                .thenReturn(PreComputeResponse.builder()
                        .exitCause(PRE_COMPUTE_DATASET_URL_MISSING)
                .build());

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(PRE_COMPUTE_DATASET_URL_MISSING));
    }

    @Test
    void shouldNotComputeSinceFailedLasStart() {
        TaskDescription taskDescription = TaskDescription.builder()
                .isTeeTask(true)
                .build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(teeMockedService.prepareTeeForTask(CHAIN_TASK_ID))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(TEE_PREPARATION_FAILED));
    }

    @Test
    void shouldNotComputeSinceFailedAppCompute() {
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
                .thenReturn(PreComputeResponse.builder()
                        .build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder()
                        .exitCause(APP_COMPUTE_FAILED)
                        .exitCode(5)
                        .stdout("stdout")
                        .build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failureWithDetails(
                        ReplicateStatusDetails.builder()
                                .cause(APP_COMPUTE_FAILED)
                                .exitCode(5)
                                .computeLogs(ComputeLogs.builder().stdout("stdout").build())
                                .build()));
    }

    @Test
    void shouldNotComputeSinceFailedPostCompute() {
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
                .thenReturn(PreComputeResponse.builder().build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder().stdout("stdout").build());
        when(computeManagerService.runPostCompute(any(), any()))
                .thenReturn(PostComputeResponse.builder()
                        .exitCause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE)
                        .build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failureWithStdout(POST_COMPUTE_FAILED_UNKNOWN_ISSUE, null));
    }
    //endregion

    //region contribute
    @Test
    void shouldContribute() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .success(chainReceipt));
    }

    @Test
    void shouldNotContributeSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(CONTRIBUTION_TIMEOUT));
    }

    @Test
    void shouldNotContributeSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(TASK_DESCRIPTION_NOT_FOUND));
    }

    @Test
    void shouldNotContributeSinceNotEnoughGas() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(OUT_OF_GAS));
    }

    @Test
    void shouldNotContributeSinceNoComputedFile() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(DETERMINISM_HASH_NOT_FOUND));
    }

    @Test
    void shouldNotContributeSinceNoContribution() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(ENCLAVE_SIGNATURE_NOT_FOUND));
    }

    @Test
    void shouldNotContributeSinceFailedToContribute() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    void shouldNotContributeSinceInvalidContributeChainReceipt() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }
    //endregion

    //region reveal
    @Test
    void shouldReveal() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.success(chainReceipt));
    }

    @Test
    void shouldNotRevealSinceNoConsensusBlock() {
        long consensusBlock = 0;
        TaskNotificationExtra extra = TaskNotificationExtra.builder().blockNumber(consensusBlock).build();

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, extra);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CONSENSUS_BLOCK_MISSING));
    }

    @Test
    void shouldNotRevealSinceNoExtraForRetrievingConsensusBlock() {
        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID, null);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CONSENSUS_BLOCK_MISSING));
    }

    @Test
    void shouldNotRevealSinceEmptyResultDigest() {
        long consensusBlock = 20;
        String resultDigest = "";
        ComputedFile computedFile = ComputedFile.builder().resultDigest(resultDigest).build();
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.reveal(CHAIN_TASK_ID,
                        TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND));
    }

    @Test
    void shouldNotRevealSinceConsensusBlockNotReached() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(BLOCK_NOT_REACHED));
    }

    @Test
    void shouldNotRevealSinceCannotReveal() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CANNOT_REVEAL));
    }


    @Test
    void shouldNotRevealSinceFailedToReveal() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    void shouldNotRevealSinceInvalidRevealChainReceipt() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }
    //endregion

    //region uploadResult
    @Test
    void shouldUploadResultWithResultUri() {
        String resultUri = "resultUri";
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn(resultUri);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.success(resultUri, ""));
    }

    @Test
    void shouldUploadResultWithResultUriAndCallbackData() {
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

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.success(resultUri, callbackData));
    }

    @Test
    void shouldNotUploadResultSinceEmptyResultLink() {
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn("");

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(RESULT_LINK_MISSING));
    }
    //endregion

    //region complete
    @Test
    void shouldComplete() {
        when(resultService.purgeTask(CHAIN_TASK_ID)).thenReturn(true);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.complete(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.success());

        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotCompleteSinceCannotRemoveResult() {
        when(resultService.purgeTask(CHAIN_TASK_ID)).thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.complete(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure());

        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }
    //endregion

    //region abort
    @Test
    void shouldAbortTask() {
        when(purgeService.purgeAllServices(CHAIN_TASK_ID)).thenReturn(true);

        assertThat(taskManagerService.abort(CHAIN_TASK_ID)).isTrue();
        verify(dockerService).stopRunningContainersWithNamePredicate(predicateCaptor.capture());
        verify(subscriptionService).unsubscribeFromTopic(CHAIN_TASK_ID);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
        // Check the predicate
        assertThat(predicateCaptor.getValue().test(CHAIN_TASK_ID)).isTrue();
    }

    //endregion

    //TODO clean theses
    //misc

    @Test
    void shouldFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        assertThat(taskManagerService.hasEnoughGas()).isTrue();
    }

    @Test
    void shouldNotFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        assertThat(taskManagerService.hasEnoughGas()).isFalse();
    }

    @Test
    void shouldFindChainReceiptValid() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(5,
                "txHash"));

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldFindChainReceiptNotValidSinceBlockIsZero() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(0,
                "txHash"));

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isFalse();
    }
}