package com.iexec.worker.chain;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.result.MetadataResult;
import com.iexec.worker.result.ResultService;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;

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

    // main test that should be valid, all other tests are failing cases of this one
    @Test
    public void shouldRevealAllValid() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isTrue();
    }

    @Test
    public void shouldNotRevealSinceChainTaskStatusWrong() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.COMPLETED)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceConsensusDeadlineReached() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), -1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceRevealDeadlineReached() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), -1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceChainContributionStatusWrong() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), -1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.UNSET)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceHashDoesntMatchConsensus(){
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(Hash.sha3("different hash value"))
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceContributionResultHashWrong() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);
        String contributionSeal = HashUtils.concatenateAndHash(walletAddress, chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(Hash.sha3("Dummy contribution value"))
                        .resultSeal(contributionSeal)
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceContributionResultSealWrong() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        Credentials credentials = Credentials.create(privateKey);
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);

        Optional<ChainContribution> optionalChainContribution = Optional.of(
                ChainContribution.builder()
                        .status(ChainContributionStatus.CONTRIBUTED)
                        .resultHash(contributionValue)
                        .resultSeal(Hash.sha3("Dummy contribution seal"))
                        .build());
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(optionalChainContribution);
        when(resultService.getMetaDataResult(chainTaskId)).thenReturn(MetadataResult.builder()
                .deterministHash(deterministHash)
                .build());
        when(credentialsService.getCredentials()).thenReturn(credentials);

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceCannotFindChainTask() {
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }

    @Test
    public void shouldNotRevealSinceCannotFindChainContribution() {
        String deterministHash = Hash.sha3("Hello");
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String contributionValue = HashUtils.concatenateAndHash(chainTaskId, deterministHash);

        Optional<ChainTask> optionalChainTask = Optional.of(
                ChainTask.builder()
                        .status(ChainTaskStatus.REVEALING)
                        .consensusDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .revealDeadline(DateUtils.addDays(new Date(), 1).getTime())
                        .consensusValue(contributionValue)
                        .build());
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(optionalChainTask);
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.empty());

        assertThat(revealService.canReveal(chainTaskId)).isFalse();
    }
}
