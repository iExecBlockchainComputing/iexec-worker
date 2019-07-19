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
import com.iexec.worker.dataset.DatasetService;
import com.iexec.worker.docker.ComputationService;
import com.iexec.worker.docker.CustomDockerClient;
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
import static com.iexec.common.replicate.ReplicateStatusCause.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


public class TaskManagerServiceTests {

    @Mock private DatasetService datasetService;
    @Mock private ResultService resultService;
    @Mock private ContributionService contributionService;
    @Mock private CustomFeignClient customFeignClient;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private IexecHubService iexecHubService;
    @Mock private CustomDockerClient customDockerClient;
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

    @Test
    public void ShouldAppTypeBeDocker() {
        String error = computationService.checkAppType(CHAIN_TASK_ID, DappType.DOCKER);

        assertThat(error).isEmpty();
    }

    @Test
    public void shouldDownloadApp() {
        TaskDescription task = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(computationService.downloadApp(CHAIN_TASK_ID, task))
                .thenReturn(true);

        boolean isAppDownloaded = taskManagerService.downloadApp(CHAIN_TASK_ID);

        assertThat(isAppDownloaded).isTrue();
    }

    @Test
    public void shouldDownloadData() {
        TaskDescription task = getStubTaskDescription(false);
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());
        when(datasetService.downloadDataset(CHAIN_TASK_ID, task.getDatasetUri()))
                .thenReturn(true);

        boolean isDataDownloaded = taskManagerService.downloadData(CHAIN_TASK_ID);
        assertThat(isDataDownloaded).isTrue();
    }

    @Test
    public void shouldBeAbleToContribute() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.empty());

        String error = taskManagerService.checkContributionAbility(CHAIN_TASK_ID);
        assertThat(error).isEmpty();
    }

    @Test
    public void shouldNotBeAbleToContribute() {
        when(contributionService.getCannotContributeStatusCause(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_UNREACHABLE));

        String error = taskManagerService.checkContributionAbility(CHAIN_TASK_ID);
        assertThat(error).isEqualTo("Cannot contribute");
    }

    @Test
    public void shouldFindAppImage() {
        TaskDescription task = getStubTaskDescription(false);
        when(customDockerClient.isImagePulled(task.getAppUri())).thenReturn(true);
        
        String error = taskManagerService.checkIfAppImageExists(CHAIN_TASK_ID, task.getAppUri());
        assertThat(error).isEmpty();
    }

    @Test
    public void shouldGetDeterminismHash() {
        String expectedHash = "expectedHash";

        when(resultService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn(expectedHash);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(false);
        
        String hash = taskManagerService.getTaskDeterminismHash(CHAIN_TASK_ID);
        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    public void shouldNotGetNonTeeDeterminismHash() {
        when(resultService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn("");
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(false);

        String hash = taskManagerService.getTaskDeterminismHash(CHAIN_TASK_ID);
        assertThat(hash).isEmpty();
        verify(customFeignClient, times(1)).updateReplicateStatus(CHAIN_TASK_ID, CANT_CONTRIBUTE,
                DETERMINISM_HASH_NOT_FOUND);
    }

    @Test
    public void shouldNotGetTeeDeterminismHash() {
        when(resultService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn("");
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        String hash = taskManagerService.getTaskDeterminismHash(CHAIN_TASK_ID);
        assertThat(hash).isEmpty();
        verify(customFeignClient, times(1)).updateReplicateStatus(CHAIN_TASK_ID, CANT_CONTRIBUTE,
                TEE_EXECUTION_NOT_VERIFIED);
    }

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



    //TODO ACTIVATE these tests

    /*
    @Test
    public void shouldNotAddReplicateWhenTaskNotInitializedOnchain() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(false);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(false)));

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldNotAddReplicateWhenTeeRequiredButNotSupported() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(true)));
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(customFeignClient, never())
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldAddReplicateWithNoTeeRequired() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(false)));
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(customFeignClient, times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    @Test
    public void shouldAddReplicateWithTeeRequired() {
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(true)));
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(workerConfigurationService.isTeeEnabled()).thenReturn(true);

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(customFeignClient, times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING);
    }

    // compute()

    @Test
    public void shouldComputeNonTeeTask() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(false)));
        when(workerConfigurationService.isTeeEnabled()).thenReturn(false);
        when(computationService.checkAppType(any(), any())).thenReturn("");
        when(taskManagerService.downloadApp(any())).thenReturn(true);
        when(taskManagerService.downloadData(any())).thenReturn(true);
        when(taskManagerService.checkContributionAbility(any())).thenReturn("");
        when(taskManagerService.checkIfAppImageExists(any(), any())).thenReturn("");

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(computationService, times(1))
                .runNonTeeComputation(any(), any());
    }

    @Test
    public void shouldComputeTeeTask() {
        when(contributionService.isChainTaskInitialized(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getTaskDescriptionFromChain(CHAIN_TASK_ID))
                .thenReturn(Optional.of(getStubTaskDescription(true)));
        when(workerConfigurationService.isTeeEnabled()).thenReturn(true);
        when(computationService.checkAppType(any(), any())).thenReturn("");
        when(taskManagerService.downloadApp(any())).thenReturn(true);
        when(taskManagerService.downloadData(any())).thenReturn(true);
        when(taskManagerService.checkContributionAbility(any())).thenReturn("");
        when(taskManagerService.checkIfAppImageExists(any(), any())).thenReturn("");

        CompletableFuture<Boolean> future = taskExecutorService.addReplicate(getStubAuth(TEE_ENCLAVE_CHALLENGE));
        future.join();

        verify(computationService, times(1))
                .runTeeComputation(any(), any());
    }

    */

    //END TODO

    // contribute()

    @Test
    public void shouldContribute() {
        ContributionAuthorization contributionAuth = getStubAuth(NO_TEE_ENCLAVE_CHALLENGE);
        String hash = "hash";
        Signature enclaveSignature = new Signature();

        when(taskManagerService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn(hash);
        when(taskManagerService.getVerifiedEnclaveSignature(CHAIN_TASK_ID, NO_TEE_ENCLAVE_CHALLENGE))
                .thenReturn(Optional.of(new Signature()));
        when(taskManagerService.checkContributionAbility(CHAIN_TASK_ID)).thenReturn("");
        when(taskManagerService.checkGasBalance(CHAIN_TASK_ID)).thenReturn(true);

        taskManagerService.contribute(contributionAuth.getChainTaskId());

        verify(contributionService, times(1)).contribute(contributionAuth, hash, enclaveSignature);
    }

    // reveal()

    @Test
    public void shouldReveal() {
        String hash = "hash";
        long consensusBlock = 55;

        when(taskManagerService.getTaskDeterminismHash(CHAIN_TASK_ID)).thenReturn(hash);
        when(revealService.isConsensusBlockReached(CHAIN_TASK_ID, consensusBlock)).thenReturn(true);
        when(revealService.canReveal(CHAIN_TASK_ID, hash)).thenReturn(true);
        when(taskManagerService.checkGasBalance(CHAIN_TASK_ID)).thenReturn(true);

        taskManagerService.reveal(CHAIN_TASK_ID, TaskNotificationExtra.builder().blockNumber(consensusBlock).build());

        verify(revealService, times(1)).reveal(CHAIN_TASK_ID, hash);
    }

    // uploadResult()

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

    @Test
    public void shouldUpdateReplicateAfterUploadResult() {
        String chainTaskId = "chainTaskId";
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("resultUri")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(chainTaskId)).thenReturn(false);
        when(resultService.uploadResult(chainTaskId)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(chainTaskId)).thenReturn(details.getChainCallbackData());

        taskManagerService.uploadResult(chainTaskId);

        verify(customFeignClient, never())
                .updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
        verify(customFeignClient, times(1))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOADED, details);
    }

    @Test
    public void shouldNotUpdateReplicateAfterUploadingResultSinceEmptyUri() {
        String chainTaskId = "chainTaskId";
        ReplicateDetails details = ReplicateDetails.builder()
                .resultLink("")
                .chainCallbackData("callbackData")
                .build();

        when(resultService.isResultEncryptionNeeded(chainTaskId)).thenReturn(false);
        when(resultService.uploadResult(chainTaskId)).thenReturn(details.getResultLink());
        when(resultService.getCallbackDataFromFile(chainTaskId)).thenReturn(details.getChainCallbackData());

        taskManagerService.uploadResult(chainTaskId);

        verify(customFeignClient, times(1))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOAD_FAILED);
        verify(customFeignClient, times(0))
                .updateReplicateStatus(chainTaskId, RESULT_UPLOADED, details);
    }

}