package com.iexec.worker.result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.iexec.common.result.ComputedFile;
import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.compute.ComputationService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomResultFeignClient;

import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ResultService {

    private WorkerConfigurationService workerConfigService;
    private PublicConfigurationService publicConfigService;
    private CredentialsService credentialsService;
    private IexecHubService iexecHubService;
    private CustomResultFeignClient customResultFeignClient;
    private ComputationService computationService;

    private Map<String, ResultInfo> resultInfoMap;

    public ResultService(WorkerConfigurationService workerConfigService,
                         PublicConfigurationService publicConfigService,
                         CredentialsService credentialsService,
                         IexecHubService iexecHubService,
                         CustomResultFeignClient customResultFeignClient,
                         ComputationService computationService) {
        this.workerConfigService = workerConfigService;
        this.publicConfigService = publicConfigService;
        this.credentialsService = credentialsService;
        this.iexecHubService = iexecHubService;
        this.customResultFeignClient = customResultFeignClient;
        this.computationService = computationService;
        this.resultInfoMap = new ConcurrentHashMap<>();
    }

    public ResultInfo getResultInfos(String chainTaskId) {
        return resultInfoMap.get(chainTaskId);
    }

    public String getResultFolderPath(String chainTaskId) {
        return workerConfigService.getTaskIexecOutDir(chainTaskId);
    }

    public boolean isResultFolderFound(String chainTaskId) {
        return new File(getResultFolderPath(chainTaskId)).exists();
    }

    public String getResultZipFilePath(String chainTaskId) {
        return getResultFolderPath(chainTaskId) + ".zip";
    }

    public String getEncryptedResultFilePath(String chainTaskId) {
        return getResultFolderPath(chainTaskId) + ".zip";
    }

    public boolean isResultZipFound(String chainTaskId) {
        return new File(getResultZipFilePath(chainTaskId)).exists();
    }

    public boolean isEncryptedResultZipFound(String chainTaskId) {
        return new File(getEncryptedResultFilePath(chainTaskId)).exists();
    }

    public void saveResultInfo(String chainTaskId, TaskDescription taskDescription) {
        ComputedFile computedFile = computationService.getComputedFile(chainTaskId);
        ResultInfo resultInfo = ResultInfo.builder()
                .image(taskDescription.getAppUri())
                .cmd(taskDescription.getCmd())
                .deterministHash(computedFile != null ? computedFile.getResultDigest() : "")
                .datasetUri(taskDescription.getDatasetUri())
                .build();

        resultInfoMap.put(chainTaskId, resultInfo);
    }

    public ResultModel getResultModelWithZip(String chainTaskId) {
        ResultInfo resultInfo = getResultInfos(chainTaskId);
        byte[] zipResultAsBytes = new byte[0];
        String zipLocation = getResultZipFilePath(chainTaskId);
        try {
            zipResultAsBytes = Files.readAllBytes(Paths.get(zipLocation));
        } catch (IOException e) {
            log.error("Failed to get zip result [chainTaskId:{}, zipLocation:{}]", chainTaskId, zipLocation);
        }

        return ResultModel.builder()
                .chainTaskId(chainTaskId)
                .image(resultInfo.getImage())
                .cmd(resultInfo.getCmd())
                .zip(zipResultAsBytes)
                .deterministHash(resultInfo.getDeterministHash())
                .build();
    }

    public boolean removeResult(String chainTaskId) {
        boolean deletedInMap = resultInfoMap.remove(chainTaskId) != null;
        boolean deletedTaskFolder = FileHelper.deleteFolder(workerConfigService.getTaskBaseDir(chainTaskId));

        boolean deleted = deletedInMap && deletedTaskFolder;
        if (deletedTaskFolder) {
            log.info("The result of the chainTaskId has been deleted [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("The result of the chainTaskId couldn't be deleted [chainTaskId:{}, deletedInMap:{}, " +
                            "deletedTaskFolder:{}]",
                    chainTaskId, deletedInMap, deletedTaskFolder);
        }

        return deleted;
    }

    public void cleanUnusedResultFolders(List<String> recoveredTasks) {
        for (String chainTaskId : getAllChainTaskIdsInResultFolder()) {
            if (!recoveredTasks.contains(chainTaskId)) {
                removeResult(chainTaskId);
            }
        }
    }

    public List<String> getAllChainTaskIdsInResultFolder() {
        File resultsFolder = new File(workerConfigService.getWorkerBaseDir());
        String[] chainTaskIdFolders = resultsFolder.list((current, name) -> new File(current, name).isDirectory());

        if (chainTaskIdFolders == null || chainTaskIdFolders.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(chainTaskIdFolders);
    }

    public String uploadResultAndGetLink(String chainTaskId) {

        if (iexecHubService.isTeeTask(chainTaskId)){//result is already uploaded
            String resultStorageProvider = iexecHubService.getTaskDescription(chainTaskId).getResultStorageProvider();
            String requester = iexecHubService.getTaskDescription(chainTaskId).getRequester();
            if (resultStorageProvider == null || resultStorageProvider.isEmpty()){
                resultStorageProvider = "ipfs";
            }
            //TODO Get link
            return String.format("{" +
                    "\"resultStorageProvider\":\"%s\", " +
                    "\"resultStoragePrivateSpaceOwner\":\"%s\", " +
                    "\"taskId\":\"%s\"" +
                    "}", resultStorageProvider, requester , chainTaskId);
        }

        String authorizationToken = getIexecUploadToken();
        if (authorizationToken.isEmpty()) {
            log.error("Empty authorizationToken, cannot upload result [chainTaskId:{}]", chainTaskId);
            return "";
        }

        log.info("Got upload authorization token [chainTaskId:{}]", chainTaskId);
        return customResultFeignClient.uploadResult(authorizationToken, getResultModelWithZip(chainTaskId));
    }

    public String getIexecUploadToken() {
        // get challenge
        Integer chainId = publicConfigService.getChainId();
        Optional<Eip712Challenge> oEip712Challenge = customResultFeignClient.getResultChallenge(chainId);

        if (!oEip712Challenge.isPresent()) {
            return "";
        }

        Eip712Challenge eip712Challenge = oEip712Challenge.get();

        // sign challenge
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();
        String signedEip712Challenge = Eip712ChallengeUtils.buildAuthorizationToken(eip712Challenge,
                workerConfigService.getWorkerWalletAddress(), ecKeyPair);

        if (signedEip712Challenge.isEmpty()) {
            return "";
        }

        // login
        return customResultFeignClient.login(chainId, signedEip712Challenge);
    }

    public boolean isResultAvailable(String chainTaskId) {
        return isResultZipFound(chainTaskId);
    }
}
