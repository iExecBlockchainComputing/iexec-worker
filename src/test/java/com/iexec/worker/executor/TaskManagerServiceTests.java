package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.contribution.Contribution;
import com.iexec.common.dapp.DappType;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateActionResponse;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.feign.CustomCoreFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class TaskManagerServiceTests {

    @Mock private DataService dataService;
    @Mock private ResultService resultService;
    @Mock private ContributionService contributionService;
    @Mock private CustomCoreFeignClient customCoreFeignClient;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private IexecHubService iexecHubService;
    @Mock private ComputationService computationService;
    @Mock private RevealService revealService;

    @InjectMocks
    private TaskManagerService taskManagerService;

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String TEE_ENCLAVE_CHALLENGE = "enclaveChallenge";
    private static final String NO_TEE_ENCLAVE_CHALLENGE = BytesUtils.EMPTY_ADDRESS;

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
        when(computationService.downloadApp(CHAIN_TASK_ID, taskDescription)).thenReturn(true);

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
    public void shouldComputeSinceNonTeeComputation() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computationService.isAppDownloaded(taskDescription.getAppUri())).thenReturn(true);
        WorkerpoolAuthorization workerpoolAuthorization = new WorkerpoolAuthorization();
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(workerpoolAuthorization);
        when(computationService.runNonTeeComputation(taskDescription, workerpoolAuthorization)).thenReturn(true);

        ReplicateActionResponse actionResponse = taskManagerService.compute(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(computationService, never()).runTeeComputation(any(), any());
    }

    @Test
    public void shouldComputeSinceTeeComputation() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computationService.isAppDownloaded(taskDescription.getAppUri())).thenReturn(true);
        WorkerpoolAuthorization workerpoolAuthorization = new WorkerpoolAuthorization();
        when(contributionService.getWorkerpoolAuthorization(CHAIN_TASK_ID)).thenReturn(workerpoolAuthorization);
        when(computationService.runTeeComputation(taskDescription, workerpoolAuthorization)).thenReturn(true);

        ReplicateActionResponse actionResponse = taskManagerService.compute(CHAIN_TASK_ID);

        assertThat(actionResponse.isSuccess()).isTrue();
        verify(computationService, never()).runNonTeeComputation(any(), any());
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

        when(resultService.getComputedFile(CHAIN_TASK_ID)).thenReturn(ComputedFile.builder().resultDigest(hash).build());
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock)).thenReturn(true);
        when(revealService.repeatCanReveal(CHAIN_TASK_ID, hash)).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);

        taskManagerService.reveal(CHAIN_TASK_ID, TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        verify(revealService, times(1)).reveal(CHAIN_TASK_ID, hash);
    }

    @Test
    public void shouldUploadResultWithoutEncrypting() {
        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(ComputedFile.builder().callbackData(details.getChainCallbackData()).build());

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldEncryptAndUploadResult() {
        ReplicateStatusDetails details = ReplicateStatusDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.uploadResultAndGetLink(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getComputedFile(CHAIN_TASK_ID))
                .thenReturn(ComputedFile.builder().callbackData(details.getChainCallbackData()).build());

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, times(1)).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotUploadResultSinceNotEncryptedWhenNeeded() {
        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(false);

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).uploadResultAndGetLink(CHAIN_TASK_ID);
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