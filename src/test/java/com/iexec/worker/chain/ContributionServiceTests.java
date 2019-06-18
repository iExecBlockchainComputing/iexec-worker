package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

import static com.iexec.worker.chain.ContributionService.computeResultHash;
import static com.iexec.worker.chain.ContributionService.computeResultSeal;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ContributionServiceTests {

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private ContributionService contributionService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldChainTaskBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(new ChainTask()));

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isTrue();
    }

    @Test
    public void shouldChainTaskNotBeInitialized() {
        String chainTaskId = "0xabc";
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.isChainTaskInitialized(chainTaskId)).isFalse();
    }

    // @Test
    // public void ShouldReturnTrueSinceIsEnclaveSignatureValid() {
    //     String chainTaskId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";
    //     String worker = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    //     String deterministHash = "0xb7e58c9d6fbde4420e87af44786ec46c797123d0667b72920b4cead23d60188b";

    //     String resultHash = computeResultHash(chainTaskId, deterministHash);
    //     String resultSeal = computeResultSeal(worker, chainTaskId, deterministHash);

    //     String enclaveAddress = "0x3cB738D98D7A70e81e81B0811Fae2452BcA049Bc";

    //     int v = 27;
    //     String r = "0xfe0d8948ca8739b0926ed5729532686b283755a1c1e660abf1ebd6362d1545c8";
    //     String s = "0x14e53d7cd66ec0a1cfe330b1e16e460ae354d33fb84cf9d62213b10c109f0db5";

    //     Signature enclaveSignature = new Signature(BytesUtils.stringToBytes(r), BytesUtils.stringToBytes(s), (byte) v);

    //     assertThat(contributionService.isEnclaveSignatureValid(resultHash, resultSeal, enclaveSignature, enclaveAddress)).isTrue();
    // }

    /**
     *  getCannotContributeStatus()
     */

    @Test
    public void getCannotContributeStatusShouldReturnStatusSinceChainTaskMissing() {
        String chainTaskId = "chainTaskId";

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).get())
                .isEqualTo(ReplicateStatus.CANT_CONTRIBUTE_SINCE_CHAIN_UNREACHABLE);
    }

    @Test
    public void getCannotContributeStatusShouldReturnStakeTooLoww() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).get())
                .isEqualTo(ReplicateStatus.CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW);
    }

    @Test
    public void getCannotContributeStatusShouldReturnTaskNotActive() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(false);

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).get())
                .isEqualTo(ReplicateStatus.CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE);
    }

    @Test
    public void getCannotContributeStatusShouldReturnAfterDeadline() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() - 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).get())
                .isEqualTo(ReplicateStatus.CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE);
    }

    @Test
    public void getCannotContributeStatusShouldReturnContributionAlreadySet() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                .status(ChainContributionStatus.CONTRIBUTED).build()));

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).get()).isEqualTo(ReplicateStatus.CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET);
    }

    @Test
    public void getCannotContributeStatusShouldReturnEmpty() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .contributionDeadline(new Date().getTime() + 1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId))
                .thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount())
                .thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId))
                .thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.isChainTaskActive(chainTaskId)).thenReturn(true);
        when(iexecHubService.getChainContribution(chainTaskId))
                .thenReturn(Optional.of(ChainContribution.builder()
                .status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCannotContributeStatus(chainTaskId).isPresent()).isFalse();
    }

    /**
     *  isContributionAuthorizationValid()
     */

    @Test
    public void shouldContributionAuthorizationBeValid() {
        // PRIVATE_KEY_STRING: "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
        // PUBLIC_KEY_STRING: "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";
        String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        Signature signature = new Signature(
                BytesUtils.stringToBytes("0x99f6b19da6aeb2133763a11204b9895c5b7d0478d08ae3d889a6bd6c820b612f"),
                BytesUtils.stringToBytes("0x0b64b1f9ceb8472f4944da55d3b75947a04618bae5ddd57a7a2a2d14c3802b7e"),
                (byte) 27);

        ContributionAuthorization contribAuth = ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveWallet)
                .signature(signature)
                .build();

        assertThat(contributionService.isContributionAuthorizationValid(contribAuth, signingAddress)).isTrue();
    }
}
