package com.iexec.worker.replicate;

import java.util.Optional;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.BytesUtils;
import com.iexec.worker.chain.ContributionService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.feign.CustomFeignClient;
import com.iexec.worker.utils.MultiAddressHelper;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ReplicateService {

    private ContributionService contributionService;
    private IexecHubService iexecHubService;
    private String corePublicAddress;

    public ReplicateService(ContributionService contributionService,
                            IexecHubService iexecHubService,
                            CustomFeignClient customFeignClient) {
        this.contributionService = contributionService;
        this.iexecHubService = iexecHubService;

        corePublicAddress = customFeignClient.getPublicConfiguration().getSchedulerPublicAddress();
    }

    public Optional<AvailableReplicateModel> contributionAuthToReplicate(ContributionAuthorization contribAuth) {

        // No task available;
        if (contribAuth == null) return Optional.empty();

        String chainTaskId = contribAuth.getChainTaskId();
        log.info("Received task [chainTaskId:{}]", chainTaskId);

        // verify that the ContributionAuthorization is valid
        if (!contributionService.isContributionAuthorizationValid(contribAuth, corePublicAddress)) {
            log.error("The contribution contribAuth is NOT valid, the task will not be performed"
                    + " [chainTaskId:{}, contribAuth:{}]", chainTaskId, contribAuth);
            return Optional.empty();
        }

        return retrieveAvailableReplicateModelFromContribAuth(contribAuth);
    }

    public Optional<AvailableReplicateModel> retrieveAvailableReplicateModelFromContribAuth(
            ContributionAuthorization contribAuth) {

        Optional<ChainTask> optionalChainTask = iexecHubService.getChainTask(contribAuth.getChainTaskId());
        if (!optionalChainTask.isPresent()) {
            log.info("Failed to retrieve AvailableReplicate, ChainTask error  [chainTaskId:{}]",
                    contribAuth.getChainTaskId());
            return Optional.empty();
        }

        ChainTask chainTask = optionalChainTask.get();

        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
        if (!optionalChainDeal.isPresent()) {
            log.info("Failed to retrieve AvailableReplicate, ChainDeal error  [chainTaskId:{}]", contribAuth.getChainTaskId());
            return Optional.empty();
        }

        ChainDeal chainDeal = optionalChainDeal.get();

        String datasetURI = chainDeal.getChainDataset() != null ? MultiAddressHelper.convertToURI(chainDeal.getChainDataset().getUri()) : "";

        return Optional.of(AvailableReplicateModel.builder()
                .contributionAuthorization(contribAuth)
                .appType(DappType.DOCKER)
                .appUri(BytesUtils.hexStringToAscii(chainDeal.getChainApp().getUri()))
                .cmd(chainDeal.getParams().get(chainTask.getIdx()))
                .maxExecutionTime(chainDeal.getChainCategory().getMaxExecutionTime())
                .isTrustedExecution(TeeUtils.isTeeTag(chainDeal.getTag()))
                .datasetUri(datasetURI)
                .build());
    }
}