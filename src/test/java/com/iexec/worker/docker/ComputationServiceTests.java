package com.iexec.worker.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.IexecHubService;
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

public class ComputationServiceTests {

    @Mock private SmsService smsService;
    @Mock private DataService dataService;
    @Mock private CustomDockerClient customDockerClient;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private ResultService resultService;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private PublicConfigurationService publicConfigurationService;
    @Mock private IexecHubService iexecHubService;


    @InjectMocks
    private ComputationService computationService;

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String TEE_ENCLAVE_CHALLENGE = "enclaveChallenge";
    private static final String NO_TEE_ENCLAVE_CHALLENGE = BytesUtils.EMPTY_ADDRESS;
    private static final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker";

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

//     TaskDescription getStubTaskDescription(boolean isTeeTask) {
//         return TaskDescription.builder()
//                 .chainTaskId(CHAIN_TASK_ID)
//                 .appType(DappType.DOCKER)
//                 .appUri("appUri")
//                 .datasetUri("datasetUri")
//                 .isTeeTask(isTeeTask)
//                 .maxExecutionTime(500000) // 5min
//                 .cmd("ls")
//                 .inputFiles(new ArrayList<>())
//                 .teePostComputeImage("registry/post-compute-app:tag")
//                 .build();
//     }

//     WorkerpoolAuthorization getStubAuth(String enclaveChallenge) {
//         return WorkerpoolAuthorization.builder()
//                 .chainTaskId(CHAIN_TASK_ID)
//                 .enclaveChallenge(enclaveChallenge)
//                 .build();
//     }

//     @Test
//     public void ShouldAppTypeBeDocker() {
//         assertThat(computationService.isValidAppType(CHAIN_TASK_ID, DappType.DOCKER)).isTrue();
//     }

//     @Test
//     public void shouldDownloadApp() {
//         String imageUri = "imageUri";
//         when(customDockerClient.pullImage(CHAIN_TASK_ID, imageUri)).thenReturn(true);
//         assertThat(computationService.downloadApp(CHAIN_TASK_ID,
//                 TaskDescription.builder()
//                 .appUri(imageUri)
//                 .appType(DappType.DOCKER)
//                 .build()
//         )).isTrue();
//     }

//     // runNonTeeComputation()

//     @Test
//     public void shouldComputeNonTeeTaskWithoutDecryptingDataset() {
//         TaskDescription task = getStubTaskDescription(false);
//         TaskSecrets mockSecrets = mock(TaskSecrets.class);

//         when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
//         when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(false);
//         when(customDockerClient.execute(any()))
//                 .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"));
//         when(resultService.saveResult(any(), any(), any())).thenReturn(true);

//         boolean isComputed = computationService.runNonTeeComputation(task,
//                 getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

//         assertThat(isComputed).isTrue();
//         verify(dataService, never()).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
//     }

//     @Test
//     public void shouldDecryptDatasetAndComputeNonTeeTask() {
//         TaskDescription task = getStubTaskDescription(false);
//         TaskSecrets mockSecrets = mock(TaskSecrets.class);

//         when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
//         when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
//         when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(true);
//         when(customDockerClient.execute(any()))
//                 .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"));
//         when(resultService.saveResult(any(), any(), any())).thenReturn(true);


//         boolean isComputed = computationService.runNonTeeComputation(task,
//                 getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

//         assertThat(isComputed).isTrue();
//         verify(dataService, times(1)).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
//     }

//     @Test
//     public void shouldNotComputeNonTeeTaskSinceCouldNotDecryptDataset() {
//         TaskDescription task = getStubTaskDescription(false);
//         TaskSecrets mockSecrets = mock(TaskSecrets.class);

//         when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
//         when(dataService.isDatasetDecryptionNeeded(CHAIN_TASK_ID)).thenReturn(true);
//         when(dataService.decryptDataset(CHAIN_TASK_ID, task.getDatasetUri())).thenReturn(false);
//         when(customDockerClient.execute(any())).thenReturn(DockerExecutionResult.failure());

//         boolean isComputed = computationService.runNonTeeComputation(task,
//                 getStubAuth(NO_TEE_ENCLAVE_CHALLENGE));

//         assertThat(isComputed).isFalse();
//         verify(dataService, times(1)).decryptDataset(CHAIN_TASK_ID, task.getDatasetUri());
//     }

//     // runTeeComputation

//     @Test
//     public void shouldComputeTeeTask() {
//         TaskDescription task = getStubTaskDescription(false);
//         WorkerpoolAuthorization workerpoolAuthorization = getStubAuth(TEE_ENCLAVE_CHALLENGE);
//         String awesomeSessionId = "awesomeSessionId";
//         ArrayList<String> stubSconeEnv = new ArrayList<>();
//         stubSconeEnv.add("fooBar");

//         when(smsService.createTeeSession(any()))
//                 .thenReturn(awesomeSessionId);
//         when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(stubSconeEnv);
//         when(customDockerClient.pullImage(anyString(), anyString())).thenReturn(true);
//         when(customDockerClient.execute(any()))
//                 .thenReturn(DockerExecutionResult.success("Computed successfully !", "containerName"))
//                 .thenReturn(DockerExecutionResult.success("Encrypted successfully !", "containerName"));
//         when(resultService.saveResult(any(), any(), any())).thenReturn(true);

//         boolean isComputed = computationService.runTeeComputation(task, workerpoolAuthorization);

//         assertThat(isComputed).isTrue();
//     }

//     @Test
//     public void shouldNotComputeTeeTaskSinceFailedToCreateSconeSession() {
//         TaskDescription task = getStubTaskDescription(false);
//         WorkerpoolAuthorization workerpoolAuthorization = getStubAuth(TEE_ENCLAVE_CHALLENGE);

//         when(smsService.createTeeSession(any()))
//                 .thenReturn("");

//         boolean isComputed = computationService.runTeeComputation(task, workerpoolAuthorization);
//         assertThat(isComputed).isFalse();
//     }

//     @Test
//     public void shouldNotComputeTeeTaskSinceFailedToBuildSconeDockerEnv() {
//         TaskDescription task = getStubTaskDescription(false);
//         WorkerpoolAuthorization workerpoolAuthorization = getStubAuth(TEE_ENCLAVE_CHALLENGE);
//         String awesomeSessionId = "awesomeSessionId";

//         when(smsService.createTeeSession(any()))
//                 .thenReturn(awesomeSessionId);
//         when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(new ArrayList<>());

//         boolean isComputed = computationService.runTeeComputation(task, workerpoolAuthorization);
//         assertThat(isComputed).isFalse();
//     }

//     @Test
//     public void shouldNotComputeTeeTaskSinceFirstRunFailed() {
//         TaskDescription task = getStubTaskDescription(false);
//         WorkerpoolAuthorization workerpoolAuthorization = getStubAuth(TEE_ENCLAVE_CHALLENGE);
//         String awesomeSessionId = "awesomeSessionId";
//         ArrayList<String> stubSconeEnv = new ArrayList<>();
//         stubSconeEnv.add("fooBar");

//         when(smsService.createTeeSession(any()))
//                 .thenReturn(awesomeSessionId);
//         when(sconeTeeService.buildSconeDockerEnv(any(), any(), any())).thenReturn(stubSconeEnv);
//         when(customDockerClient.execute(any())).thenReturn(DockerExecutionResult.failure());

//         boolean isComputed = computationService.runTeeComputation(task, workerpoolAuthorization);

//         assertThat(isComputed).isFalse();
//     }

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFile(){
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile = computationService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo("0x09b727883db89fa3b3504f83e0c67d04a0d4fc35a9670cc4517c49d2a27ad171");
    }

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFileTree(){
        String chainTaskId = "deterministic-output-directory";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile = computationService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        System.out.println(hash);
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo("0xc6114778cc5c33db5fbbd4d0f9be116ed0232961045341714aba5a72d3ef7402");
    }

    @Test
    public void shouldGetComputedFileWithWeb3ResultDigest(){
        String chainTaskId = "callback-directory";

        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(true).build());

        ComputedFile computedFile = computationService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        System.out.println(hash);
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo("0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6");
    }

    //TODO Update after that
    @Test
    public void shouldComputeResultDigest(){

    }
}