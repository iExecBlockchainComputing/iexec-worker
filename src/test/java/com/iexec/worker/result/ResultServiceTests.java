package com.iexec.worker.result;

import com.iexec.common.task.TaskDescription;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomResultFeignClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.iexec.common.chain.DealParams.DROPBOX_RESULT_STORAGE_PROVIDER;
import static com.iexec.common.chain.DealParams.IPFS_RESULT_STORAGE_PROVIDER;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ResultServiceTests {

    private static final String TASK_ID = "taskId";
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private CustomResultFeignClient customResultFeignClient;

    @InjectMocks
    private ResultService resultService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetTeeWeb2ResultLinkSinceIpfs() {
        String storage = IPFS_RESULT_STORAGE_PROVIDER;
        String ipfsHash = "QmcipfsHash";

        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());
        when(customResultFeignClient.getIpfsHashForTask(TASK_ID)).thenReturn(ipfsHash);

        String resultLink = resultService.getWeb2ResultLink(TASK_ID);

        assertThat(resultLink.equals(resultService.buildResultLink(storage, "/ipfs/" + ipfsHash))).isTrue();
    }

    @Test
    public void shouldGetTeeWeb2ResultLinkSinceDropbox() {
        String storage = DROPBOX_RESULT_STORAGE_PROVIDER;

        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());

        String resultLink = resultService.getWeb2ResultLink(TASK_ID);

        assertThat(resultLink.equals(resultService.buildResultLink(storage, "/results/" + TASK_ID))).isTrue();
    }

    @Test
    public void shouldNotGetTeeWeb2ResultLinkSinceBadStorage() {
        String storage = "some-unsupported-third-party-storage";

        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());

        String resultLink = resultService.getWeb2ResultLink(TASK_ID);

        assertThat(resultLink.isEmpty()).isTrue();
    }


    @Test
    public void shouldNotGetTeeWeb2ResultLinkSinceNoTask() {
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(null);

        String resultLink = resultService.getWeb2ResultLink(TASK_ID);

        assertThat(resultLink.isEmpty()).isTrue();
    }


}
