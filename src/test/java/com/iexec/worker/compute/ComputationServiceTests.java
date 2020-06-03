package com.iexec.worker.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.result.ResultService;
import com.iexec.worker.sms.SmsService;
import com.iexec.worker.tee.scone.SconeTeeService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ComputationServiceTests {

    private static final String CHAIN_TASK_ID = "0xfoobar";
    private static final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker";
    private static final String SECURE_SESSION = "abcdef";

    @Rule
    public TemporaryFolder jUnitTemporaryFolder = new TemporaryFolder();

    @Mock private SmsService smsService;
    @Mock private DataService dataService;
    @Mock private DockerService dockerService;
    @Mock private SconeTeeService sconeTeeService;
    @Mock private ResultService resultService;
    @Mock private WorkerConfigurationService workerConfigurationService;
    @Mock private PublicConfigurationService publicConfigurationService;
    @Mock private IexecHubService iexecHubService;

    @InjectMocks
    private ComputeService computeService;

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

    WorkerpoolAuthorization getStubAuth(String enclaveChallenge) {
        return WorkerpoolAuthorization.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveChallenge)
                .build();
    }

    @Test
    public void ShouldAppTypeBeDocker() {
        assertThat(computeService.isValidAppType(CHAIN_TASK_ID, DappType.DOCKER)).isTrue();
    }

    @Test
    public void shouldDownloadApp() {
        String imageUri = "imageUri";
        when(dockerService.pullImage(CHAIN_TASK_ID, imageUri)).thenReturn(true);
        assertThat(computeService.downloadApp(CHAIN_TASK_ID,
                TaskDescription.builder()
                .appUri(imageUri)
                .appType(DappType.DOCKER)
                .build()
        )).isTrue();
    }

    // Pre compute

    @Test
    public void shouldPassTeePreCompute() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        WorkerpoolAuthorization workerpoolAuth = new WorkerpoolAuthorization();
        when(dockerService.pullImage(taskDescription.getChainTaskId(), taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuth)).thenReturn(SECURE_SESSION);

        computeService.runPreCompute(compute, taskDescription, workerpoolAuth);
        verify(smsService, times(1)).createTeeSession(workerpoolAuth);
        assertThat(compute.isPreComputed()).isTrue();
    }

    @Test
    public void shouldFailTeePreComputeTeeTaskSinceFailedToCreateSconeSession() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        WorkerpoolAuthorization workerpoolAuth = new WorkerpoolAuthorization();
        when(dockerService.pullImage(taskDescription.getChainTaskId(), taskDescription.getTeePostComputeImage()))
                .thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuth)).thenReturn("");

        computeService.runPreCompute(compute, taskDescription, workerpoolAuth);
        verify(smsService, times(1)).createTeeSession(workerpoolAuth);
        assertThat(compute.isPreComputed()).isFalse();
    }

    @Test
    public void shouldPassNonTeePreCompute() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        Compute compute = new Compute();
        WorkerpoolAuthorization workerpoolAuth = new WorkerpoolAuthorization();
        TaskSecrets mockSecrets = mock(TaskSecrets.class);
        when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
        when(dataService.isDatasetDecryptionNeeded(taskDescription.getChainTaskId())).thenReturn(true);
        when(dataService.decryptDataset(taskDescription.getChainTaskId(), taskDescription.getDatasetUri())).thenReturn(true);

        computeService.runPreCompute(compute, taskDescription, workerpoolAuth);
        verify(smsService).fetchTaskSecrets(workerpoolAuth);
        verify(dataService).decryptDataset(CHAIN_TASK_ID, taskDescription.getDatasetUri());
        assertThat(compute.isPreComputed()).isTrue();
    }

    @Test
    public void shouldFailNonTeePreComputeSinceCouldNotDecryptDataset() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        Compute compute = new Compute();
        WorkerpoolAuthorization workerpoolAuth = new WorkerpoolAuthorization();
        TaskSecrets mockSecrets = mock(TaskSecrets.class);
        when(smsService.fetchTaskSecrets(any())).thenReturn(Optional.of(mockSecrets));
        when(dataService.isDatasetDecryptionNeeded(taskDescription.getChainTaskId())).thenReturn(true);
        when(dataService.decryptDataset(taskDescription.getChainTaskId(), taskDescription.getDatasetUri())).thenReturn(false);

        computeService.runPreCompute(compute, taskDescription, workerpoolAuth);
        verify(smsService).fetchTaskSecrets(workerpoolAuth);
        verify(dataService).decryptDataset(CHAIN_TASK_ID, taskDescription.getDatasetUri());
        assertThat(compute.isPreComputed()).isFalse();
   }

    // Compute

    @Test
    public void shouldPassTeeCompute() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.of("success !"));

        computeService.runCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isTrue();
        assertThat(compute.isComputed()).isTrue();
    }

    @Test
    public void shouldFailTeeComputesinceDockerExecutionFailed() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.empty());

        computeService.runCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isTrue();
        assertThat(compute.isComputed()).isFalse();
    }

    @Test
    public void shouldPassNonTeeCompute() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.of("success !"));

        computeService.runCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isFalse();
        assertThat(compute.isComputed()).isTrue();
    }

    @Test
    public void shouldFailNonTeeComputesinceDockerExecutionFailed() {
        TaskDescription taskDescription = getStubTaskDescription(false);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.empty());

        computeService.runCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isFalse();
        assertThat(compute.isComputed()).isFalse();
    }

    // Post compute

    @Test
    public void shouldPassTeePostCompute() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.of("success !"));

        computeService.runPostCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isTrue();
        assertThat(compute.isPostComputed()).isTrue();
    }

    @Test
    public void shouldFailTeePostComputeSinceDockerExecutionFailed() {
        TaskDescription taskDescription = getStubTaskDescription(true);
        Compute compute = new Compute();
        when(dockerService.run(any()))
                .thenReturn(Optional.empty());

        computeService.runPostCompute(compute, taskDescription);
        ArgumentCaptor<DockerCompute> argumentCaptor = ArgumentCaptor.forClass(DockerCompute.class);
        verify(dockerService).run(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().get(0).isSgx()).isTrue();
        assertThat(compute.isPostComputed()).isFalse();
    }

    @Test
    public void shouldPassNonTeePostCompute() throws Exception {
        TaskDescription taskDescription = getStubTaskDescription(false);
        String output = jUnitTemporaryFolder.newFolder().getAbsolutePath();
        String iexecOut = output + FileHelper.SLASH_IEXEC_OUT;
        String computedJson = iexecOut + IexecFileHelper.SLASH_COMPUTED_JSON;
        new File(iexecOut).mkdir();
        new File(computedJson).createNewFile();
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        Compute compute = new Compute();
        when(workerConfigurationService.getTaskIexecOutDir(taskDescription.getChainTaskId()))
                .thenReturn(iexecOut);
        when(workerConfigurationService.getTaskOutputDir(taskDescription.getChainTaskId()))
                .thenReturn(output);

        computeService.runPostCompute(compute, taskDescription);
        System.out.println(FileHelper.printDirectoryTree(new File(output)));
        assertThat(new File(output + "/iexec_out.zip")).exists();
        assertThat(new File(output + IexecFileHelper.SLASH_COMPUTED_JSON)).exists();
        assertThat(compute.isPostComputed()).isTrue();
    }

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFile(){
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
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

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
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

        ComputedFile computedFile = computeService.getComputedFile(chainTaskId);
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