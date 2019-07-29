package com.iexec.worker.docker;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;


public class ComputationServiceTests {

    @Mock private SmsService smsService;
    @Mock private DataService dataService;
    @Mock private CustomDockerClient customDockerClient;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private ResultService resultService;

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
                .maxExecutionTime(500)
                .cmd("ls")
                .inputFiles(new ArrayList<>())
                .build();
    }

    ContributionAuthorization getStubAuth(String enclaveChallenge) {
        return ContributionAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
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
        String expectedStdout = "Computed successfully !";

        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);

        boolean isComputed = computationService.runNonTeeComputation(task,
        when(customDockerClient.runNonTeeTaskContainer(any())).thenReturn(expectedStdout);

        Pair<ReplicateStatus, String> result = computationService.runNonTeeComputation(task,
                getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

        assertThat(isComputed).isTrue();
        verify(dataService, never()).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
    }

    @Test
    public void shouldDecryptDatasetAndComputeNonTeeTask() {
        TaskDescription task = getStubTaskDescription(false);
        String expectedStdout = "Computed successfully !";

        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(true);
        when(customDockerClient.runNonTeeTaskContainer(any())).thenReturn(expectedStdout);

        boolean isComputed = computationService.runNonTeeComputation(task,
                getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

        assertThat(isComputed).isTrue();
        verify(dataService, times(1)).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
    }

    @Test
    public void shouldNotComputeNonTeeTaskSinceCouldnotDecryptDataset() {
        TaskDescription task = getStubTaskDescription(false);
        String expectedStdout = "Failed to decrypt dataset, URI:" + task.getDatasetUri();

        when(smsService.fetchTaskSecrets(any())).thenReturn(true);
        when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
        when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(false);
        when(customDockerClient.runNonTeeTaskContainer(any())).thenReturn(expectedStdout);

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
        String expectedStdout1 = "Computed successfully 1 !";
        String expectedStdout2 = "Computed successfully 1 !";
        String awesomeSessionId = "awesomeSessionId";
        ArrayList<String> stubSconeEnv = new ArrayList<>();
        stubSconeEnv.add("fooBar");

        when(sconeTeeService.createSconeSecureSession(contributionAuth))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(anyString())).thenReturn(stubSconeEnv);
        when(customDockerClient.runTeeTaskContainer(any())).thenReturn(expectedStdout1)
                                                           .thenReturn(expectedStdout2);

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isTrue();
    }

    @Test
    public void shouldNotComputeTeeTaskSinceFailedToCreateSconeSession() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String expectedStdout = "Could not generate scone secure session for tee computation";

        when(sconeTeeService.createSconeSecureSession(contributionAuth)).thenReturn("");

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isFalse();
    }

    @Test
    public void shouldNotComputeTeeTaskSinceFailedToBuildSconeDockerEnv() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String expectedStdout = "Could not create scone docker environment";
        String awesomeSessionId = "awesomeSessionId";

        when(sconeTeeService.createSconeSecureSession(contributionAuth))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(anyString())).thenReturn(new ArrayList<>());

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isFalse();
    }

    // @Test
    // public void shouldNotComputeTeeTaskSinceFailedToBuildSconeContainerConfig() {
    //     TaskDescription task = getStubTaskDescription(false);
    //     ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
    //     String expectedStdout = "Could not build scone container config";
    //     String awesomeSessionId = "awesomeSessionId";
    //     ArrayList<String> stubSconeEnv = new ArrayList<>();
    //     stubSconeEnv.add("fooBar");

    //     when(sconeTeeService.createSconeSecureSession(contributionAuth))
    //             .thenReturn(awesomeSessionId);
    //     when(sconeTeeService.buildSconeDockerEnv(anyString())).thenReturn(stubSconeEnv);
    //     when(customDockerClient.buildSconeAppContainerConfig(any(), any(), any(), any())).thenReturn(null);

    //     Pair<ReplicateStatus, String> result = computationService.runTeeComputation(task, contributionAuth);

    //     assertThat(result.getLeft()).isEqualTo(COMPUTE_FAILED);
    //     assertThat(result.getRight()).isEqualTo(expectedStdout);
    // }

    @Test
    public void shouldNotComputeTeeTaskSinceFirstRunFailed() {
        TaskDescription task = getStubTaskDescription(false);
        ContributionAuthorization contributionAuth = getStubAuth(TEE_ENCLAVE_CHALLENGE);
        String expectedStdout = "Failed to start computation";
        String awesomeSessionId = "awesomeSessionId";
        ArrayList<String> stubSconeEnv = new ArrayList<>();
        stubSconeEnv.add("fooBar");

        when(sconeTeeService.createSconeSecureSession(contributionAuth))
                .thenReturn(awesomeSessionId);
        when(sconeTeeService.buildSconeDockerEnv(anyString())).thenReturn(stubSconeEnv);
        when(customDockerClient.runTeeTaskContainer(any())).thenReturn("");

        boolean isComputed = computationService.runTeeComputation(task, contributionAuth);

        assertThat(isComputed).isFalse();
    }

    @Test
    public void ShouldAppTypeBeDocker() {
        assertThat(computationService.isValidAppType(CHAIN_TASK_ID, DappType.DOCKER)).isTrue();
    }

}