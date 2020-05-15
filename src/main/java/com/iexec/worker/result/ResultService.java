package com.iexec.worker.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveChallengeSignature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.feign.CustomResultFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.utils.BytesUtils.bytesToString;
import static com.iexec.common.utils.FileHelper.createFileWithContent;
import static com.iexec.common.utils.SignatureUtils.isExpectedSignerOnSignedMessageHash;
import static com.iexec.common.worker.result.ResultUtils.getCallbackDataFromPath;

@Slf4j
@Service
public class ResultService {

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
        ResultInfo resultInfo = ResultInfo.builder()
                .image(taskDescription.getAppUri())
                .cmd(taskDescription.getCmd())
                .deterministHash(getTaskDeterminismHash(chainTaskId))
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
        return getResultFolderPath(chainTaskId) + FileHelper.SLASH_IEXEC_OUT + ".zip";
    }

    // public String getResultDirPath(String chainTaskId, boolean isTeeTask) {
    //     return getResultFolderPath(chainTaskId) +
    // }

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

    public String getTaskDeterminismHash(String chainTaskId) {
        boolean isTeeTask = iexecHubService.isTeeTask(chainTaskId);

        if (isTeeTask){
            return getTeeDeterminismHash(chainTaskId);
        } else {
            return getNonTeeDeterminismHash(chainTaskId);
        }
    }

    private String getNonTeeDeterminismHash(String chainTaskId) {
        String hash = "";
        String filePath = workerConfigService.getTaskIexecOutDir(chainTaskId)
                + File.separator + DETERMINIST_FILE_NAME;

        Path determinismFilePath = Paths.get(filePath);

        try {
            String callbackFilePathName = workerConfigService.getTaskIexecOutDir(chainTaskId)
                    + File.separator + CALLBACK_FILE_NAME;

            String callbackData = getCallbackDataFromPath(callbackFilePathName);
            if (!callbackData.isEmpty()){
                return Hash.sha3(callbackData);
            }

            if (determinismFilePath.toFile().exists()) {
                hash = getHashFromDeterminismIexecFile(determinismFilePath);
                log.info("Determinism file found and its hash has been computed "
                        + "[chainTaskId:{}, hash:{}]", chainTaskId, hash);
                return hash;
            }

            log.info("Determinism file not found, the hash of the result file will be used instead "
                    + "[chainTaskId:{}]", chainTaskId);
            String resultFilePathName = getResultZipFilePath(chainTaskId);
            byte[] content = Files.readAllBytes(Paths.get(resultFilePathName));
            hash = bytesToString(Hash.sha256(content));
        } catch (IOException e) {
            log.error("Failed to compute determinism hash [chainTaskId:{}]", chainTaskId);
            e.printStackTrace();
            return "";
        }

        log.info("Computed hash of the result file [chainTaskId:{}, hash:{}]", chainTaskId, hash);
        return hash;
    }

    /** This method is to compute the hash of the determinist.iexec file
     * if the file is a text and if it is a byte32, no need to hash it
     * if the file is a text and if it is NOT a byte32, it is hashed using sha256
     * if the file is NOT a text, it is hashed using sha256
     */
    private String getHashFromDeterminismIexecFile(Path deterministFilePath) throws IOException {
        try (Scanner scanner = new Scanner(deterministFilePath.toFile())) {
            // command to put the content of the whole file into string (\Z is the end of the string anchor)
            // This ultimately makes the input have one actual token, which is the entire file
            String contentFile = scanner.useDelimiter("\\Z").next();
            byte[] content = BytesUtils.stringToBytes(contentFile);

            // if determinism.iexec file is already a byte32, no need to hash it again
            return bytesToString(BytesUtils.isBytes32(content) ? content : Hash.sha256(content));

        } catch (Exception e) {
            return bytesToString(Hash.sha256(Files.readAllBytes(deterministFilePath)));
        }
    }

    private String getTeeDeterminismHash(String chainTaskId) {
        Optional<TeeEnclaveChallengeSignature> enclaveChallengeSignature =
                readTeeEnclaveChallengeSignatureFile(chainTaskId);

        if (!enclaveChallengeSignature.isPresent()) {
            log.error("Could not get TEE determinism hash [chainTaskId:{}]", chainTaskId);
            return "";
        }

        String hash = enclaveChallengeSignature.get().getResultDigest();
        log.info("Enclave signature file found, result hash retrieved successfully "
                + "[chainTaskId:{}, hash:{}]", chainTaskId, hash);
        return hash;
    }

    public Optional<TeeEnclaveChallengeSignature> readTeeEnclaveChallengeSignatureFile(String chainTaskId) {
        String enclaveSignatureFilePath = workerConfigService.getTaskIexecOutDir(chainTaskId)
                + File.separator + TEE_ENCLAVE_SIGNATURE_FILE_NAME;

        File enclaveSignatureFile = new File(enclaveSignatureFilePath);

        if (!enclaveSignatureFile.exists()) {
            log.error("File enclaveSig.iexec not found [chainTaskId:{}, enclaveSignatureFilePath:{}]",
                    chainTaskId, enclaveSignatureFilePath);
            return Optional.empty();
        }

        ObjectMapper mapper = new ObjectMapper();
        TeeEnclaveChallengeSignature enclaveChallengeSignature = null;
        try {
            enclaveChallengeSignature = mapper.readValue(enclaveSignatureFile, TeeEnclaveChallengeSignature.class);
        } catch (IOException e) {
            log.error("File enclaveSig.iexec found but failed to parse it [chainTaskId:{}]", chainTaskId);
            e.printStackTrace();
            return Optional.empty();
        }

        if (enclaveChallengeSignature == null) {
            log.error("File enclaveSig.iexec found but was parsed to null [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        log.debug("Content of enclaveSig.iexec file [chainTaskId:{}, enclaveChallengeSignature:{}]",
                chainTaskId, enclaveChallengeSignature);

        return Optional.of(enclaveChallengeSignature);
    }

    public boolean isExpectedSignerOnSignature(String messageHash, Signature signature, String expectedSigner) {
        return isExpectedSignerOnSignedMessageHash(messageHash,
                signature,
                expectedSigner);
    }

    public String getCallbackDataFromFile(String chainTaskId) {
        String callbackFilePathName = workerConfigService.getTaskIexecOutDir(chainTaskId)
                + File.separator + CALLBACK_FILE_NAME;

        String callbackData = getCallbackDataFromPath(callbackFilePathName);
        if (!callbackData.isEmpty()){
            log.info("Callback file exists [chainTaskId:{}, callbackFilePathName:{}]", chainTaskId, callbackFilePathName);
        } else {
            log.info("No callback file [chainTaskId:{}, callbackFilePathName:{}]", chainTaskId, callbackFilePathName);
        }

        return callbackData;
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
