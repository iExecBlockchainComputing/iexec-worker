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
import com.iexec.common.dapp.DappType;
import com.iexec.common.notification.TaskNotificationExtra;
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
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


public class TaskManagerServiceTests {

    private static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";

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
        MockitoAnnotations.initMocks(this);
    }

    TaskDescription getStubTaskDescription(boolean isTeeTask) {
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .appType(DappType.DOCKER)
                .appUri("appUri")
                .datasetUri("datasetUri")
                .isTeeTask(isTeeTask)
                .build();
    }

    WorkerpoolAuthorization getStubAuth(String enclaveChallenge) {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
    }

    /*
     *
     * TODO Add should not
     *
     * */

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
    public void shouldNotDownloadAppSinceAppNotDownloaded() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(computeManagerService.downloadApp(taskDescription))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
    }

    // no dataset

    /**
     * Note : Remember dataset URI is optional
     */
    @Test
    public void shouldDownloadData() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotDownloadDataSinceCannotContributeStatusIsPresent() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CONTRIBUTION_TIMEOUT));

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotDownloadDataSinceNoTaskDescription() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(null);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
        assertThat(actionResponse.getDetails().getCause())
                .isEqualTo(TASK_DESCRIPTION_NOT_FOUND);
    }

    // with dataset

    @Test
    public void shouldDownloadDataWithDatasetUri() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(true);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotDownloadDataWithDatasetUriSinceCannotDownload() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
    }

    // with dataset + TEE

    @Test
    public void shouldDownloadDataWithDatasetUriAndTee() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(true);
        when(dataService.unzipDownloadedTeeDataset(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(true);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotDownloadDataWithDatasetUriAndTeeSinceCannotUnzip() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(true);
        when(dataService.unzipDownloadedTeeDataset(CHAIN_TASK_ID, taskDescription.getDatasetUri()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
    }

    // with input files

    @Test
    public void shouldDownloadDataWithInputFiles() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles()))
                .thenReturn(true);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldNotDownloadDataWithInputFilesSinceCannotDownload() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        taskDescription.setDatasetUri("");
        taskDescription.setInputFiles(Collections.singletonList("https://ab.cd/ef.jpeg"));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID))
                .thenReturn(taskDescription);
        when(dataService.downloadFiles(CHAIN_TASK_ID,
                taskDescription.getInputFiles()))
                .thenReturn(false);

        ReplicateActionResponse actionResponse =
                taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isFalse();
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
        when(computeManagerService.getComputedFile(CHAIN_TASK_ID))
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
    public void shouldContributeSinceTeeComputation() {
        //TODO
    }

    @Test
    public void shouldContributeSinceNonTeeComputation() {
        //TODO
    }

    @Test
    public void shouldReveal() {
        String hash = "hash";
        long consensusBlock = 55;

        when(computeManagerService.getComputedFile(CHAIN_TASK_ID)).thenReturn(
                ComputedFile.builder().resultDigest(hash).build());
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID,
                consensusBlock)).thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, hash)).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);

        taskManagerService.reveal(CHAIN_TASK_ID,
                TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        verify(revealService, times(1)).reveal(CHAIN_TASK_ID, hash);
    }

    @Test
    public void shouldUploadResult() {
        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID))
                .thenReturn(details.getResultLink());
        when(computeManagerService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(ComputedFile.builder()
                        .callbackData(details.getChainCallbackData())
                        .build()
                );

        taskManagerService.uploadResult(CHAIN_TASK_ID);
        verify(resultService).uploadResultAndGetLink(CHAIN_TASK_ID);
    }

    //TODO clean theses
    //misc

    @Test
    public void shouldFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        assertThat(taskManagerService.checkGasBalance()).isTrue();
    }

    @Test
    public void shouldNotFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        assertThat(taskManagerService.checkGasBalance()).isFalse();
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