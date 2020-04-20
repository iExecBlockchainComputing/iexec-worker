package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.sms.secrets.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Optional;


public class ComputationServiceTests {

    @Mock private SmsService smsService;
    @Mock private DataService dataService;
    @Mock private CustomDockerClient customDockerClient;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private ResultService resultService;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private PublicConfigurationService publicConfigurationService;


    @InjectMocks
    private ComputationService computationService;

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
                .maxExecutionTime(500000) // 5min
                .cmd("ls")
                .inputFiles(new ArrayList<>())
                .teePostComputeImage("registry/post-compute-app:tag")
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
        assertThat(computationService.isValidAppType(CHAIN_TASK_ID, DappType.DOCKER)).isTrue();
    }

    @Test
    public void shouldDownloadApp() {
        String imageUri = "imageUri";
        when(customDockerClient.pullImage(CHAIN_TASK_ID, imageUri)).thenReturn(true);
        assertThat(computationService.downloadApp(CHAIN_TASK_ID,
                TaskDescription.builder()
                .appUri(imageUri)
                .appType(DappType.DOCKER)
                .build()
        )).isTrue();
    }

    // runNonTeeComputation()

    @Test
    public void shouldComputeNonTeeTaskWithoutDecryptingDataset() {
        TaskDescription task = getStubTaskDescription(false);
        TaskSecrets mockSecrets = mock(TaskSecrets.class);

        when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);
        when(customDockerClient.execute(any()))
                .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"));
        when(resultService.saveResult(any(), any(), any())).thenReturn(true);

        boolean isComputed = computationService.runNonTeeComputation(task,
                getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

        assertThat(isComputed).isTrue();
        verify(dataService, never()).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
    }

    @Test
    public void shouldDecryptDatasetAndComputeNonTeeTask() {
        TaskDescription task = getStubTaskDescription(false);
        TaskSecrets mockSecrets = mock(TaskSecrets.class);

        when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(true);
        when(customDockerClient.execute(any()))
                .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"));
        when(resultService.saveResult(any(), any(), any())).thenReturn(true);


        boolean isComputed = computationService.runNonTeeComputation(task,
                getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

        assertThat(isComputed).isTrue();
        verify(dataService, times(1)).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
    }

    @Test
    public void shouldNotComputeNonTeeTaskSinceCouldNotDecryptDataset() {
        TaskDescription task = getStubTaskDescription(false);
        TaskSecrets mockSecrets = mock(TaskSecrets.class);

        when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(false);
        when(customDockerClient.execute(any())).thenReturn(DockerExecutionResult.failure());

        boolean isComputed = computationService.runNonTeeComputation(task,
                getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

        assertThat(isComputed).isFalse();
        verify(dataService, times(1)).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
    }

    // runTeeComputation

    @Test
    public void shouldComputeTeeTask() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String awesomeSessionId = "awesomeSessionId";
        ArrayList<String> stubSconeEnv = new ArrayList<>();
        stubSconeEnv.add("fooBar");

        when(sconeTeeService.createSconeSecureSession(any()))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(stubSconeEnv);
        when(customDockerClient.pullImage(anyString(), anyString())).thenReturn(true);
        when(customDockerClient.execute(any()))
                .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"))
                .thenReturn(DockerExecutionResult.success("Encrypted successfully !", "containerName"));
        when(resultService.saveResult(any(), any(), any())).thenReturn(true);

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isTrue();
    }

    @Test
    public void shouldNotComputeTeeTaskSinceFailedToCreateSconeSession() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);

        when(sconeTeeService.createSconeSecureSession(any()))
                .thenReturn("");

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);
        assertThat(isComputed).isFalse();
    }

    @Test
    public void shouldNotComputeTeeTaskSinceFailedToBuildSconeDockerEnv() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String awesomeSessionId = "awesomeSessionId";

        when(sconeTeeService.createSconeSecureSession(any()))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(new ArrayList<>());

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);
        assertThat(isComputed).isFalse();
    }

    @Test
    public void shouldNotComputeTeeTaskSinceFirstRunFailed() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String awesomeSessionId = "awesomeSessionId";
        ArrayList<String> stubSconeEnv = new ArrayList<>();
        stubSconeEnv.add("fooBar");

        when(sconeTeeService.createSconeSecureSession(any()))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(stubSconeEnv);
        when(customDockerClient.execute(any())).thenReturn(DockerExecutionResult.failure());

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isFalse();
    }
}