package com.iexec.worker.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.common.worker.result.ResultUtils;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomResultFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.utils.BytesUtils.bytesToString;
import static com.iexec.common.utils.FileHelper.createFileWithContent;
import static com.iexec.common.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;

@Slf4j
@Service
public class ResultService {

    @Value("${encryptFilePath}")
    private String scriptFilePath;

    private static final String DETERMINIST_FILE_NAME = "determinism.iexec";
    private static final String TEE_ENCLAVE_SIGNATURE_FILE_NAME = "enclaveSig.iexec";
    private static final String CALLBACK_FILE_NAME = "callback.iexec";
    private static final String STDOUT_FILENAME = "stdout.txt";

    private WorkerConfigurationService workerConfigService;
    private PublicConfigurationService publicConfigService;
    private CredentialsService credentialsService;
    private IexecHubService iexecHubService;
    private CustomResultFeignClient customResultFeignClient;

    private Map<String, ResultInfo> resultInfoMap;

    public ResultService(WorkerConfigurationService workerConfigService,
                         PublicConfigurationService publicConfigService,
                         CredentialsService credentialsService,
                         IexecHubService iexecHubService,
                         CustomResultFeignClient customResultFeignClient) {
        this.workerConfigService = workerConfigService;
        this.publicConfigService = publicConfigService;
        this.credentialsService = credentialsService;
        this.iexecHubService = iexecHubService;
        this.customResultFeignClient = customResultFeignClient;
        this.resultInfoMap = new ConcurrentHashMap<>();
    }

    public boolean saveResult(String chainTaskId, TaskDescription taskDescription, String stdout) {
        try {
            saveStdoutFileInResultFolder(chainTaskId, stdout);
            zipResultFolder(chainTaskId);
            saveResultInfo(chainTaskId, taskDescription);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private File saveStdoutFileInResultFolder(String chainTaskId, String stdoutContent) {
        log.info("Stdout file added to result folder [chainTaskId:{}]", chainTaskId);
        String filePath = getResultFolderPath(chainTaskId) + File.separator + STDOUT_FILENAME;
        return createFileWithContent(filePath, stdoutContent);
    }

    public void zipResultFolder(String chainTaskId) {
        File zipFile = FileHelper.zipFolder(getResultFolderPath(chainTaskId));
        log.info("Zip file has been created [chainTaskId:{}, zipFile:{}]", chainTaskId, zipFile.getAbsolutePath());
    }

    public void saveResultInfo(String chainTaskId, TaskDescription taskDescription) {
        ComputedFile computedFile = getComputedFile(chainTaskId);

        ResultInfo resultInfo = ResultInfo.builder()
                .image(taskDescription.getAppUri())
                .cmd(taskDescription.getCmd())
                .deterministHash(computedFile != null ? computedFile.getResultDigest(): "")
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

    public ResultInfo getResultInfos(String chainTaskId) {
        return resultInfoMap.get(chainTaskId);
    }

    public String getResultZipFilePath(String chainTaskId) {
        return getResultFolderPath(chainTaskId) + ".zip";
    }

    public String getResultFolderPath(String chainTaskId) {
        return workerConfigService.getTaskOutputDir(chainTaskId);
    }

    public String getEncryptedResultFilePath(String chainTaskId) {
        return workerConfigService.getTaskOutputDir(chainTaskId) + FileHelper.SLASH_IEXEC_OUT + ".zip";
    }

    public boolean isResultZipFound(String chainTaskId) {
        return new File(getResultZipFilePath(chainTaskId)).exists();
    }

    public boolean isResultFolderFound(String chainTaskId) {
        return new File(getResultFolderPath(chainTaskId)).exists();
    }

    public boolean isEncryptedResultZipFound(String chainTaskId) {
        return new File(getEncryptedResultFilePath(chainTaskId)).exists();
    }

    public boolean removeResult(String chainTaskId) {
        boolean deletedInMap = resultInfoMap.remove(chainTaskId) != null;
        boolean deletedTaskFolder = FileHelper.deleteFolder(new File(getResultFolderPath(chainTaskId)).getParent());

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

    public ComputedFile getComputedFile(String chainTaskId) {
        ComputedFile computedFile = IexecFileHelper.readComputedFile(chainTaskId,
                workerConfigService.getTaskIexecOutDir(chainTaskId));

        if (computedFile == null){
            log.error("Failed to getComputedFile (computed.json missing)[chainTaskId:{}]", chainTaskId);
            return null;
        }

        if (computedFile.getResultDigest() == null || computedFile.getResultDigest().isEmpty()){
            String resultDigest = computeResultDigest(computedFile);
            if (resultDigest.isEmpty()){
                log.error("Failed to getComputedFile (resultDigest is empty but cant compute it)" +
                                "[chainTaskId:{}, computedFile:{}]", chainTaskId, computedFile);
                return null;
            }
            computedFile.setResultDigest(resultDigest);
        }

        return computedFile;
    }

    private String computeResultDigest(ComputedFile computedFile) {
        String chainTaskId = computedFile.getTaskId() ;

        String resultDigest;
        if (iexecHubService.getTaskDescription(chainTaskId).isCallbackRequested()){
            resultDigest = ResultUtils.computeWeb3ResultDigest(computedFile);
        } else {
            resultDigest = ResultUtils.computeWeb2ResultDigest(computedFile,
                    workerConfigService.getTaskOutputDir(chainTaskId));
        }

        if (resultDigest.isEmpty()){
            log.error("Failed to getComputedFile (resultDigest empty)[chainTaskId:{}, computedFile:{}]",
                    chainTaskId, computedFile);
            return "";
        }
        return resultDigest;
    }

    public boolean isResultEncryptionNeeded(String chainTaskId) {
        String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);

        if (!new File(beneficiarySecretFilePath).exists()) {
            log.info("No beneficiary secret file found, will continue without encrypting result [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    public boolean encryptResult(String chainTaskId) {
        String beneficiarySecretFilePath = workerConfigService.getBeneficiarySecretFilePath(chainTaskId);
        String resultZipFilePath = getResultZipFilePath(chainTaskId);
        String taskOutputDir = workerConfigService.getTaskOutputDir(chainTaskId);

        log.info("Encrypting result zip [resultZipFilePath:{}, beneficiarySecretFilePath:{}]",
                resultZipFilePath, beneficiarySecretFilePath);

        encryptFile(taskOutputDir, resultZipFilePath, beneficiarySecretFilePath);

        String encryptedResultFilePath = getEncryptedResultFilePath(chainTaskId);

        if (!new File(encryptedResultFilePath).exists()) {
            log.error("Encrypted result file not found [chainTaskId:{}, encryptedResultFilePath:{}]",
                    chainTaskId, encryptedResultFilePath);
            return false;
        }

        // replace result file with the encypted one
        return FileHelper.replaceFile(resultZipFilePath, encryptedResultFilePath);
    }

    private void encryptFile(String taskOutputDir, String resultZipFilePath, String publicKeyFilePath) {
        String options = String.format("--root-dir=%s --result-file=%s --key-file=%s",
                taskOutputDir, resultZipFilePath, publicKeyFilePath);

        String cmd = this.scriptFilePath + " " + options;

        ProcessBuilder pb = new ProcessBuilder(cmd.split(" "));

        try {
            Process pr = pb.start();

            BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
    
            while ((line = in.readLine()) != null) { log.info(line); }
    
            pr.waitFor();
            in.close();
        } catch (Exception e) {
            log.error("Error while trying to encrypt result [resultZipFilePath{}, publicKeyFilePath:{}]",
                    resultZipFilePath, publicKeyFilePath);
            e.printStackTrace();
        }
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
        boolean isResultZipFound = isResultZipFound(chainTaskId);
        boolean isResultFolderFound = isResultFolderFound(chainTaskId);

        if (!isResultZipFound && !isResultFolderFound) return false;

        if (!isResultZipFound) zipResultFolder(chainTaskId);

        return true;
    }
}
