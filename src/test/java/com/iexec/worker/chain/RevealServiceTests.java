package com.iexec.worker.chain;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.worker.result.ResultService;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Hash;

import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class RevealServiceTests {

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ResultService resultService;

    @Mock
    private CredentialsService credentialsService;

    @InjectMocks
    private RevealService revealService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotRevealSinceChainTaskStatusWrong() {
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

        Optional<ChainTask> optional = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.COMPLETED)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optional);
        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceConsensusDeadlineReached() {
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

        Optional<ChainTask> optional = Optional.of(
                ChainTask.builder()
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .status(ChainTaskStatus.COMPLETED)
                        .build());
    }

    @Test
    public void shouldNotRevealSinceRevealDeadlineReached() {
        // TODO
    }

    @Test
    public void shouldNotRevealSinceChainContributionStatusWrong() {
        // TODO
    }

    @Test
    public void shouldNotRevealSinceContributionResultHashWrong() {
        // TODO
    }

    @Test
    public void shouldNotRevealSinceContributionResultSealWrong() {
        // TODO
    }

    @Test
    public void shouldRevealAllValid() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.COMPLETED)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(deterministHash)
                        .build());

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
          ChainContribution.builder()
                  .status(ChainContributionStatus.CONTRIBUTED)
                  .resultHash(deterministHash)
                  .build()
        );
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
    }
}
