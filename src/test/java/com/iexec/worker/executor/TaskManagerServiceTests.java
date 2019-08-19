package com.iexec.worker.executor;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.chain.RevealService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.tee.scone.SconeEnclaveSignatureFile;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class TaskManagerServiceTests {

    @Mock private DataService dataService;
    @Mock private ResultService resultService;
    @Mock private ContributionService contributionService;
    @Mock private CustomFeignClient customFeignClient;
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

    ContributionAuthorization getStubAuth(String enclaveChallenge) {
        return ContributionAuthorization.builder()
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

        boolean isStarted = taskManagerService.start(CHAIN_TASK_ID);

        assertThat(isStarted).isTrue();
    }

    @Test
    public void shouldDownloadApp() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computationService.downloadApp(CHAIN_TASK_ID, taskDescription)).thenReturn(true);

        boolean isAppDownloaded = taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(isAppDownloaded).isTrue();
    }

    @Test
    public void shouldDownloadData() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(dataService.downloadFile(CHAIN_TASK_ID, taskDescription.getDatasetUri())).thenReturn(true);

        boolean isDataDownloaded = taskManagerService.downloadData(CHAIN_TASK_ID);

        assertThat(isDataDownloaded).isTrue();
    }

    @Test
    public void shouldComputeSinceNonTeeComputation() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computationService.isAppDownloaded(taskDescription.getAppUri())).thenReturn(true);
        ContributionAuthorization contributionAuthorization = new ContributionAuthorization();
        when(contributionService.getContributionAuthorization(CHAIN_TASK_ID)).thenReturn(contributionAuthorization);
        when(computationService.runNonTeeComputation(taskDescription, contributionAuthorization)).thenReturn(true);

        boolean isComputed = taskManagerService.compute(CHAIN_TASK_ID);

        assertThat(isComputed).isTrue();
        verify(computationService, never()).runTeeComputation(any(), any());
    }

    @Test
    public void shouldComputeSinceTeeComputation() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(computationService.isAppDownloaded(taskDescription.getAppUri())).thenReturn(true);
        ContributionAuthorization contributionAuthorization = new ContributionAuthorization();
        when(contributionService.getContributionAuthorization(CHAIN_TASK_ID)).thenReturn(contributionAuthorization);
        when(computationService.runTeeComputation(taskDescription, contributionAuthorization)).thenReturn(true);

        boolean isComputed = taskManagerService.compute(CHAIN_TASK_ID);

        assertThat(isComputed).isTrue();
        verify(computationService, never()).runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldContributeSinceNonTeeComputation() {
        boolean isTeeTask = false;
        TaskDescription taskDescription = getStubTaskDescription(isTeeTask);
        String hash = "hash";
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(taskDescription);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        //determinism hash
        when(resultService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn(hash);
        //enclave signature
        ContributionAuthorization contributionAuthorization = getStubAuth(NO_TEE_ENCLAVE_CHALLENGE);
        when(contributionService.getContributionAuthorization(CHAIN_TASK_ID)).thenReturn(contributionAuthorization);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(isTeeTask);

        when(contributionService.contribute(contributionAuthorization, hash, SignatureUtils.emptySignature()))
                .thenReturn(Optional.of(ChainReceipt.builder().blockNumber(10).build()));

        boolean isContributed = taskManagerService.contribute(CHAIN_TASK_ID);

        assertThat(isContributed).isTrue();
    }

    @Test
    public void shouldContributeSinceTeeComputation() {
        //TODO
    }

    @Test
    public void shouldReveal() {
        String hash = "hash";
        long consensusBlock = 55;

        when(resultService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn(hash);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock)).thenReturn(true);
        when(revealService.canReveal(CHAIN_TASK_ID, hash)).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);

        taskManagerService.reveal(CHAIN_TASK_ID, TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        verify(revealService, times(1)).reveal(CHAIN_TASK_ID, hash);
    }

    @Test
    public void shouldUploadResultWithoutEncrypting() {
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);
        when(resultService.uploadResult(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(CHAIN_TASK_ID)).thenReturn(details.getChainCallbackData());

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldEncryptAndUploadResult() {
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.uploadResult(CHAIN_TASK_ID)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(CHAIN_TASK_ID)).thenReturn(details.getChainCallbackData());

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, times(1)).encryptResult(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotUploadResultSinceNotEncryptedWhenNeeded() {
        when(resultService.isResultEncryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.encryptResult(CHAIN_TASK_ID)).thenReturn(false);

        taskManagerService.uploadResult(CHAIN_TASK_ID);

        verify(resultService, never()).uploadResult(CHAIN_TASK_ID);
    }





    //TODO clean theses

    @Test
    public void shouldGetNonTeeEnclaveSignature() {
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(false);

        Optional<Signature> sign =
                taskManagerService.getVerifiedEnclaveSignature(CHAIN_TASK_ID, NO_TEE_ENCLAVE_CHALLENGE);
        assertThat(sign.get()).isEqualTo(SignatureUtils.emptySignature());
    }

    @Test
    public void shouldGetTeeVerifiedEnclaveSignature() {
        SconeEnclaveSignatureFile sconeFile = SconeEnclaveSignatureFile.builder()
                .signature("signature")
                .resultHash("resultHash")
                .resultSalt("resultSalt")
                .build();

        Signature expectedSign = new Signature(sconeFile.getSignature());

        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.readSconeEnclaveSignatureFile(CHAIN_TASK_ID))
                .thenReturn(Optional.of(sconeFile));
        when(sconeTeeService.isEnclaveSignatureValid(any(), any(), any(), any())).thenReturn(true);

        Optional<Signature> oSign =
                taskManagerService.getVerifiedEnclaveSignature(CHAIN_TASK_ID, TEE_ENCLAVE_CHALLENGE);
        assertThat(oSign.get()).isEqualTo(expectedSign);
    }

    @Test
    public void shouldNotGetTeeEnclaveSignatureSinceNotValid() {
        String enclaveChallenge = "dummyEnclaveChallenge";
        SconeEnclaveSignatureFile sconeFile = SconeEnclaveSignatureFile.builder()
                .signature("signature")
                .resultHash("resultHash")
                .resultSalt("resultSalt")
                .build();

        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);
        when(resultService.readSconeEnclaveSignatureFile(CHAIN_TASK_ID))
                .thenReturn(Optional.of(sconeFile));
        when(sconeTeeService.isEnclaveSignatureValid(any(), any(), any(), any())).thenReturn(false);

        Optional<Signature> oSign =
                taskManagerService.getVerifiedEnclaveSignature(CHAIN_TASK_ID, enclaveChallenge);
        assertThat(oSign.isPresent()).isFalse();
    }

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
        verify(customFeignClient, times(1)).updateReplicateStatus(CHAIN_TASK_ID, OUT_OF_GAS);
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