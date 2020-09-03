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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.compute.ComputeService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class TaskManagerServiceTests {

    @Mock
    private DataService dataService;
    @Mock
    private ResultService resultService;
    @Mock
    private ContributionService contributionService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private SconeTeeService sconeTeeService;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ComputeService computeService;
    @Mock
    private RevealService revealService;

    @InjectMocks
    private TaskManagerService taskManagerService;

    private static final String CHAIN_TASK_ID = "0xfoobar";

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
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(getStubTaskDescription(false));
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(sconeTeeService.isTeeEnabled()).thenReturn(false);

        ReplicateActionResponse actionResponse = taskManagerService.start(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldDownloadApp() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computeService.downloadApp(CHAIN_TASK_ID, taskDescription)).thenReturn(true);

        ReplicateActionResponse actionResponse = taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
    }

    @Test
    public void shouldDownloadData() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri())).thenReturn(true);

        ReplicateActionResponse actionResponse = taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
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

        when(computeService.getComputedFile(CHAIN_TASK_ID)).thenReturn(ComputedFile.builder().resultDigest(hash).build());
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock)).thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, hash)).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);

        taskManagerService.reveal(CHAIN_TASK_ID, TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

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
        when(computeService.getComputedFile(CHAIN_TASK_ID))
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
        assertThat(taskManagerService.checkGasBalance(CHAIN_TASK_ID)).isTrue();
    }

    @Test
    public void shouldNotFindEnoughGasBalance() {
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        assertThat(taskManagerService.checkGasBalance(CHAIN_TASK_ID)).isFalse();
    }

    @Test
    public void shouldFindChainReceiptValid() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(5, "txHash"));

        boolean isValid = taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isTrue();
    }

    @Test
    public void shouldFindChainReceiptNotValidSinceBlockIsZero() {
        Optional<ChainReceipt> receipt = Optional.of(new ChainReceipt(0, "txHash"));

        boolean isValid = taskManagerService.isValidChainReceipt(CHAIN_TASK_ID, receipt);
        assertThat(isValid).isFalse();
    }
}