package com.iexec.worker.chain;

import com.iexec.common.chain.*;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.security.TeeSignature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.chain.ChainTaskStatus.ACTIVE;
import static com.iexec.common.chain.ChainTaskStatus.REVEALING;
import static com.iexec.common.chain.ChainTaskStatus.UNSET;
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
    public void shouldContributionAuthorizationBeValid() {
        // PRIVATE_KEY_STRING: "a392604efc2fad9c0b3da43b5f698a2e3f270f170d859912be0d54742275c5f6";
        // PUBLIC_KEY_STRING: "0x506bc1dc099358e5137292f4efdd57e400f29ba5132aa5d12b18dac1c1f6aaba645c0b7b58158babbfa6c6cd5a48aa7340a8749176b120e8516216787a13dc76";
        String signingAddress = "0xef678007d18427e6022059dbc264f27507cd1ffc";

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskid = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        ContributionAuthorization contribAuth = ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskid)
                .enclave(enclaveWallet)
                .signR(BytesUtils.stringToBytes("0x99f6b19da6aeb2133763a11204b9895c5b7d0478d08ae3d889a6bd6c820b612f"))
                .signS(BytesUtils.stringToBytes("0x0b64b1f9ceb8472f4944da55d3b75947a04618bae5ddd57a7a2a2d14c3802b7e"))
                .signV((byte) 27)
                .build();

        assertThat(contributionService.isContributionAuthorizationValid(contribAuth, signingAddress)).isTrue();
    }

    @Test
    public void ShouldReturnTrueSinceIsEnclaveSignatureValid() {
        String chainTaskId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";
        String worker = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        String deterministHash = "0xb7e58c9d6fbde4420e87af44786ec46c797123d0667b72920b4cead23d60188b";

        String resultHash = computeResultHash(chainTaskId, deterministHash);
        String resultSeal = computeResultSeal(worker, chainTaskId, deterministHash);

        String enclaveAddress = "0x3cB738D98D7A70e81e81B0811Fae2452BcA049Bc";

        int v = 27;
        String r = "0xfe0d8948ca8739b0926ed5729532686b283755a1c1e660abf1ebd6362d1545c8";
        String s = "0x14e53d7cd66ec0a1cfe330b1e16e460ae354d33fb84cf9d62213b10c109f0db5";

        Sign.SignatureData enclaveSignature = new Sign.SignatureData((byte) v, BytesUtils.stringToBytes(r), BytesUtils.stringToBytes(s));

        assertThat(contributionService.isEnclaveSignatureValid(resultHash, resultSeal , enclaveSignature, enclaveAddress)).isTrue();
    }

    @Test
    public void GetCanContributeStatusShouldReturnEmptySinceChainTaskMissing() {
        String chainTaskId = "chainTaskId";

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.empty());

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isFalse();
    }

    @Test
    public void GetCanContributeStatusShouldReturnCanContribute() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .status(ACTIVE)
                .contributionDeadline(new Date().getTime()+1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.of(ChainContribution.builder().status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isTrue();
        assertThat(contributionService.getCanContributeStatus(chainTaskId).get().equals(ReplicateStatus.CAN_CONTRIBUTE)).isTrue();
    }

    @Test
    public void GetCanContributeStatusShouldReturnStakeTooLoww() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .status(ACTIVE)
                .contributionDeadline(new Date().getTime()+1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(0).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.of(ChainContribution.builder().status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isTrue();
        assertThat(contributionService.getCanContributeStatus(chainTaskId).get().equals(ReplicateStatus.CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW)).isTrue();
    }

    @Test
    public void GetCanContributeStatusShouldReturnTaskNotActive() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .status(REVEALING)
                .contributionDeadline(new Date().getTime()+1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.of(ChainContribution.builder().status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isTrue();
        assertThat(contributionService.getCanContributeStatus(chainTaskId).get().equals(ReplicateStatus.CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE)).isTrue();
    }

    @Test
    public void GetCanContributeStatusShouldReturnAfterDeadline() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .status(ACTIVE)
                .contributionDeadline(new Date().getTime()-1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.of(ChainContribution.builder().status(ChainContributionStatus.UNSET).build()));

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isTrue();
        assertThat(contributionService.getCanContributeStatus(chainTaskId).get().equals(ReplicateStatus.CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE)).isTrue();
    }

    @Test
    public void GetCanContributeStatusShouldReturnContributed() {
        String chainDealId = "0x1566a9348a284d12f7d81fa017fbc440fd501ddef5746821860ffda7113eb847";

        ChainTask chainTask = ChainTask.builder()
                .dealid(chainDealId)
                .idx(0)
                .status(ACTIVE)
                .contributionDeadline(new Date().getTime()+1000)
                .build();

        String chainTaskId = chainTask.getChainTaskId();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));
        when(iexecHubService.getChainAccount()).thenReturn(Optional.of(ChainAccount.builder().deposit(1000).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().workerStake(BigInteger.valueOf(5)).build()));
        when(iexecHubService.getChainContribution(chainTaskId)).thenReturn(Optional.of(ChainContribution.builder().status(ChainContributionStatus.CONTRIBUTED).build()));

        assertThat(contributionService.getCanContributeStatus(chainTaskId).isPresent()).isTrue();
        assertThat(contributionService.getCanContributeStatus(chainTaskId).get().equals(ReplicateStatus.CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET)).isTrue();
    }

}
