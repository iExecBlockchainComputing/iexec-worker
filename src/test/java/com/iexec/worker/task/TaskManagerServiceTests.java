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

package com.iexec.worker.task;

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.common.replicate.ComputeLogs;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.result.ComputedFile;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.dapp.DappType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.notification.TaskNotificationExtra;
import com.iexec.sms.api.TeeSessionGenerationResponse;
import com.iexec.worker.chain.Contribution;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.compute.ComputeManagerService;
import com.iexec.worker.compute.app.AppComputeResponse;
import com.iexec.worker.compute.post.PostComputeResponse;
import com.iexec.worker.compute.pre.PreComputeResponse;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.replicate.ReplicateActionResponse;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.TeeService;
import com.iexec.worker.tee.TeeServicesManager;
import com.iexec.worker.workflow.WorkflowError;
import com.iexec.worker.workflow.WorkflowException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskManagerServiceTests {

    private static final String CHAIN_TASK_ID = "0x1";
    private static final String ENCLAVE_CHALLENGE = "0x2";
    private static final String WORKER_ADDRESS = "0x3";
    private static final String PATH_TO_DOWNLOADED_FILE = "/path/to/downloaded/file";
    private static final WorkerpoolAuthorization WORKERPOOL_AUTHORIZATION =
            WorkerpoolAuthorization.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .enclaveChallenge(ENCLAVE_CHALLENGE)
                    .workerWallet(WORKER_ADDRESS)
                    .build();

    @InjectMocks
    private TaskManagerService taskManagerService;
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
    private SmsService smsService;
    @Mock
    private PurgeService purgeService;

    @Mock
    private TeeService teeMockedService;

    TaskDescription.TaskDescriptionBuilder getTaskDescriptionBuilder(boolean isTeeTask) {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(List.of("https://ab.cd/ef.jpeg"))
                .build();
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri("appUri")
                .datasetAddress("datasetAddress")
                .datasetChecksum("datasetChecksum")
                .datasetUri("datasetUri")
                .isTeeTask(isTeeTask)
                .dealParams(dealParams);
    }

    final List<WorkflowError> emptyCauses = new ArrayList<>();

    //region start
    @Test
    void shouldStartStandardTask() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(getTaskDescriptionBuilder(false).build());

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    void shouldNotStartSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(CONTRIBUTION_TIMEOUT).build()));

        ReplicateActionResponse actionResponse =
                taskManagerService.start(getTaskDescriptionBuilder(false).build());

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldNotStartSinceStandardTaskWithEncryption() {
        final DealParams dealParams = DealParams.builder()
                .iexecResultEncryption(true)
                .build();
        final TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .dealParams(dealParams)
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        ReplicateActionResponse actionResponse = taskManagerService.start(taskDescription);
        Assertions.assertThat(actionResponse.isSuccess()).isFalse();
        Assertions.assertThat(actionResponse.getDetails().getCause()).isEqualTo(TASK_DESCRIPTION_INVALID);
    }

    @Test
    void shouldStartTeeTask() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeMockedService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID)).thenReturn(emptyCauses);

        ReplicateActionResponse actionResponse =
                taskManagerService.start(getTaskDescriptionBuilder(true).build());

        assertThat(actionResponse.isSuccess()).isTrue();
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldNotStartSinceTeePrerequisitesAreNotMet() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeMockedService.areTeePrerequisitesMetForTask(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(TEE_NOT_SUPPORTED).build()));

        ReplicateActionResponse actionResponse =
                taskManagerService.start(getTaskDescriptionBuilder(true).build());

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(TEE_NOT_SUPPORTED);
    }
    //endregion

    //region downloadApp
    @Test
    void shouldDownloadApp() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(true);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldNotDownloadAppSinceCannotContributionStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(CONTRIBUTION_TIMEOUT).build()));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(getTaskDescriptionBuilder(false).build());

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithSuccess() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder().build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(APP_IMAGE_DOWNLOAD_FAILED);
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure1() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause()).isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
        verify(computeManagerService, never()).runPostCompute(any(TaskDescription.class), any(TeeSessionGenerationResponse.class));
    }

    @Test
    void shouldAppDownloadFailedAndTriggerPostComputeHookWithFailure2() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder()
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build())).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(taskDescription);

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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(CONTRIBUTION_TIMEOUT).build()));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    // without dataset + without input files

    @Test
    void shouldReturnSuccessAndNotDownloadDataSinceEmptyUrls() throws Exception {
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(null)
                .build();
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetUri("")
                .dealParams(dealParams)
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService, never()).downloadStandardDataset(taskDescription);
        verify(dataService, never()).downloadStandardInputFiles(anyString(), anyList());
    }

    // with dataset + with input files

    @Test
    void shouldDownloadDatasetAndInputFiles() throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        final DealParams dealParams = DealParams.builder()
                .iexecInputFiles(null)
                .build();
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .dealParams(dealParams)
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetAddress("")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService, never()).downloadStandardDataset(taskDescription);
        verify(dataService).downloadStandardInputFiles(anyString(), anyList());
    }

    // with dataset + with input files + TEE task

    @Test
    void shouldNotDownloadDataWithDatasetUriForTeeTaskAndReturnSuccess() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(true).build();
        final ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);
        assertThat(actionResponse.isSuccess()).isTrue();
        verifyNoInteractions(dataService);
    }

    // DATASET_FILE_DOWNLOAD_FAILED exception

    @Test
    void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithSuccess()
            throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
            throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
        verifyNoInteractions(computeManagerService);
    }

    @Test
    void shouldHandleDatasetDownloadFailureAndTriggerPostComputeHookWithFailure2()
            throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(dataService.downloadStandardDataset(taskDescription))
                .thenThrow(new WorkflowException(DATASET_FILE_DOWNLOAD_FAILED));
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder()
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build())).build());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
    }

    // with dataset and on-chain checksum

    @Test
    void shouldWithDatasetUriAndChecksumDownloadData() throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetUri("")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        doNothing().when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getDealParams().getIexecInputFiles());

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getDealParams().getIexecInputFiles());
    }

    @Test
    void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithSuccess()
            throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetUri("")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getDealParams().getIexecInputFiles());
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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetUri("")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getDealParams().getIexecInputFiles());
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(taskDescription);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(POST_COMPUTE_FAILED_UNKNOWN_ISSUE);
        verifyNoInteractions(computeManagerService);
    }

    @Test
    void shouldWithInputFilesDataDownloadFailedAndTriggerPostComputeHookWithFailure2()
            throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false)
                .datasetUri("")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        WorkflowException e = new WorkflowException(INPUT_FILES_DOWNLOAD_FAILED);
        doThrow(e).when(dataService).downloadStandardInputFiles(CHAIN_TASK_ID,
                taskDescription.getDealParams().getIexecInputFiles());
        when(resultService.writeErrorToIexecOut(anyString(), any(), any()))
                .thenReturn(true);
        when(computeManagerService.runPostCompute(taskDescription, null))
                .thenReturn(PostComputeResponse.builder()
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build())).build());

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
        final TaskDescription taskDescription = getTaskDescriptionBuilder(false).build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

        verify(computeManagerService).runPostCompute(any(), any());
        verifyNoInteractions(iexecHubService, teeServicesManager);
        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(
                        ReplicateActionResponse
                                .successWithLogs(ComputeLogs.builder()
                                        .stdout("stdout")
                                        .stderr("stderr")
                                        .build()));
        verifyNoInteractions(teeServicesManager, resultService);
    }

    @Test
    void shouldComputeTeeTask() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(true).build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
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

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

        verify(computeManagerService).runPostCompute(any(), any());
        verifyNoInteractions(iexecHubService);
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
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(replicateStatusCause).build()));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(getTaskDescriptionBuilder(false).build());

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(replicateStatusCause));
    }

    @Test
    void shouldNotComputeSinceAppNotDownloaded() {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(APP_NOT_FOUND_LOCALLY));
        verifyNoInteractions(iexecHubService, teeServicesManager);
    }

    @Test
    void shouldNotComputeSinceFailedPreCompute() {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder()
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(PRE_COMPUTE_DATASET_URL_MISSING).build()))
                        .build());

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(PRE_COMPUTE_DATASET_URL_MISSING));
    }

    @Test
    void shouldNotComputeSinceFailedLasStart() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder(true).build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(teeServicesManager.getTeeService(any())).thenReturn(teeMockedService);
        when(teeMockedService.prepareTeeForTask(CHAIN_TASK_ID))
                .thenReturn(false);

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .failure(TEE_PREPARATION_FAILED));
    }

    @Test
    void shouldNotComputeSinceFailedAppCompute() {
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(computeManagerService.isAppDownloaded(taskDescription.getAppUri()))
                .thenReturn(true);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID))
                .thenReturn(workerpoolAuthorization);
        when(computeManagerService.runPreCompute(any(), any()))
                .thenReturn(PreComputeResponse.builder().build());
        when(computeManagerService.runCompute(any(), any()))
                .thenReturn(AppComputeResponse.builder()
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(APP_COMPUTE_FAILED).build()))
                        .exitCode(5)
                        .stdout("stdout")
                        .build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

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
        final TaskDescription taskDescription = TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).build();
        WorkerpoolAuthorization workerpoolAuthorization =
                WorkerpoolAuthorization.builder().build();

        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
                        .exitCauses(List.of(WorkflowError.builder()
                                .cause(POST_COMPUTE_FAILED_UNKNOWN_ISSUE).build()))
                        .build());


        ReplicateActionResponse replicateActionResponse =
                taskManagerService.compute(taskDescription);

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
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(computedFile);
        when(contributionService.getContribution(computedFile))
                .thenReturn(contribution);
        when(contributionService.contribute(contribution))
                .thenReturn(Optional.of(chainReceipt));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse
                        .success(chainReceipt));
    }

    @Test
    void shouldNotContributeSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(CONTRIBUTION_TIMEOUT).build()));

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.contribute(CHAIN_TASK_ID);

        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CONTRIBUTION_TIMEOUT));
    }

    @Test
    void shouldNotContributeSinceNotEnoughGas() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        ChainReceipt chainReceipt =
                ChainReceipt.builder().blockNumber(0).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(emptyCauses);
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
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(WORKERPOOL_AUTHORIZATION);
        when(resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION)).thenReturn(resultUri);
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
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(WORKERPOOL_AUTHORIZATION);
        when(resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION)).thenReturn(resultUri);
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
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(WORKERPOOL_AUTHORIZATION);
        when(resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION)).thenReturn("");

        ReplicateActionResponse replicateActionResponse =
                taskManagerService.uploadResult(CHAIN_TASK_ID);

        Assertions.assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(RESULT_LINK_MISSING));
    }
    //endregion

    // region contributeAndFinalize
    @Test
    void shouldNotContributeAndFinalizeSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(CONTRIBUTION_TIMEOUT).build()));
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CONTRIBUTION_TIMEOUT));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceNotEnoughGas() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(OUT_OF_GAS));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceNoComputedFile() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(null);
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(DETERMINISM_HASH_NOT_FOUND));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceNoContribution() {
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData("data")
                .build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(null);
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(ENCLAVE_SIGNATURE_NOT_FOUND));
    }

    @Test
    void shouldNotContributeAdnFinalizeSinceTrustNotOne() {
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData("data")
                .build();
        Contribution contribution = mock(Contribution.class);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(contribution);
        when(contributionService.getCannotContributeAndFinalizeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(TRUST_NOT_1).build()));
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(TRUST_NOT_1));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceTaskAlreadyContributed() {
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData("data")
                .build();
        Contribution contribution = mock(Contribution.class);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(contribution);
        when(contributionService.getCannotContributeAndFinalizeStatusCause(CHAIN_TASK_ID))
                .thenReturn(List.of(WorkflowError.builder()
                        .cause(TASK_ALREADY_CONTRIBUTED).build()));
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(TASK_ALREADY_CONTRIBUTED));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceTransactionFailed() {
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData("data")
                .build();
        Contribution contribution = mock(Contribution.class);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(contribution);
        when(iexecHubService.contributeAndFinalize(any(), any(), any())).thenReturn(Optional.empty());
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    void shouldNotContributeAndFinalizeSinceInvalidChainReceipt() {
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData("data")
                .build();
        ChainReceipt chainReceipt = ChainReceipt.builder().blockNumber(0).build();
        Contribution contribution = mock(Contribution.class);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(contribution);
        when(iexecHubService.contributeAndFinalize(any(), any(), any())).thenReturn(Optional.of(chainReceipt));
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(ReplicateActionResponse.failure(CHAIN_RECEIPT_NOT_VALID));
    }

    @Test
    void shouldContributeAndFinalize() {
        final String data = "data";
        final String resultLink = "resultLink";
        ComputedFile computedFile = ComputedFile.builder()
                .resultDigest("digest")
                .callbackData(data)
                .build();

        Contribution contribution = mock(Contribution.class);
        ChainReceipt chainReceipt = ChainReceipt.builder().blockNumber(10).build();
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(emptyCauses);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(computedFile);
        when(contributionService.getContribution(computedFile)).thenReturn(contribution);
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(WORKERPOOL_AUTHORIZATION);
        when(resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION)).thenReturn(resultLink);
        when(iexecHubService.contributeAndFinalize(any(), anyString(), anyString())).thenReturn(Optional.of(chainReceipt));
        ReplicateActionResponse replicateActionResponse = taskManagerService.contributeAndFinalize(CHAIN_TASK_ID);

        final ReplicateStatusDetails expectedDetails = ReplicateStatusDetails.builder()
                .resultLink(resultLink)
                .chainCallbackData(data)
                .chainReceipt(chainReceipt)
                .build();
        final ReplicateActionResponse expectedResponse = ReplicateActionResponse.builder()
                .isSuccess(true)
                .details(expectedDetails)
                .build();
        assertThat(replicateActionResponse)
                .isNotNull()
                .isEqualTo(expectedResponse);
    }
    // endregion

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
    void shouldReturnFalseWhenRemainingContainers() {
        when(computeManagerService.abort(CHAIN_TASK_ID)).thenReturn(false);
        when(purgeService.purgeAllServices(CHAIN_TASK_ID)).thenReturn(true);
        assertThat(taskManagerService.abort(CHAIN_TASK_ID)).isFalse();
        verify(computeManagerService).abort(CHAIN_TASK_ID);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }

    @Test
    void shouldReturnFalseWhenRemainingService() {
        when(computeManagerService.abort(CHAIN_TASK_ID)).thenReturn(true);
        when(purgeService.purgeAllServices(CHAIN_TASK_ID)).thenReturn(false);
        assertThat(taskManagerService.abort(CHAIN_TASK_ID)).isFalse();
        verify(computeManagerService).abort(CHAIN_TASK_ID);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }

    @Test
    void shouldAbortTask() {
        when(computeManagerService.abort(CHAIN_TASK_ID)).thenReturn(true);
        when(purgeService.purgeAllServices(CHAIN_TASK_ID)).thenReturn(true);
        assertThat(taskManagerService.abort(CHAIN_TASK_ID)).isTrue();
        verify(computeManagerService).abort(CHAIN_TASK_ID);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
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
        ChainReceipt receipt = ChainReceipt.builder()
                .blockNumber(5)
                .txHash("txHash")
                .build();

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldFindChainReceiptNotValidSinceNull() {
        assertThat(taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, null)).isFalse();
    }

    @Test
    void shouldFindChainReceiptNotValidSinceBlockIsZero() {
        ChainReceipt receipt = ChainReceipt.builder()
                .blockNumber(0)
                .txHash("txHash")
                .build();

        boolean isValid =
                taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isFalse();
    }
}
