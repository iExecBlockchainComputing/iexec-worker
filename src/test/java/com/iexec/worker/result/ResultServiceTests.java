package com.iexec.worker.result;

import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.sms.SmsService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

public class ResultServiceTests {

    private final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker";

    @Mock private WorkerConfigurationService workerConfigurationService;
//     @Mock private ResultRepoService resultRepoService;
    @Mock private CredentialsService credentialsService;
    @Mock private IexecHubService iexecHubService;
    @Mock private SmsService smsService;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetComputedFileWithWeb2ResultDigestSinceFile(){
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(false).build());

        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
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


        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        System.out.println(hash);
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo("0x5efcd74e892340a0c6b0878e846e33aa92abaf23a2415fcdb3036b4b6de3024d");
    }

    @Test
    public void shouldGetComputedFileWithWeb3ResultDigest(){
        String chainTaskId = "callback-directory";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + "/" + chainTaskId + "/output/iexec_out");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(TaskDescription.builder().isCallbackRequested(true).build());


        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
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
