package com.iexec.worker.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.result.ResultModel;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.security.TeeSignature;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.common.utils.BytesUtils.bytesToString;
import static com.iexec.worker.utils.FileHelper.createFileWithContent;

@Slf4j
@Service
public class ResultService {

    private static final String DETERMINIST_FILE_NAME = "consensus.iexec";
    private static final String TEE_ENCLAVE_SIGNATURE_FILE_NAME = "enclaveSig.iexec";
    private static final String STDOUT_FILENAME = "stdout.txt";

    private Map<String, ResultInfo> resultInfoMap;
    private WorkerConfigurationService configurationService;

    public ResultService(WorkerConfigurationService configurationService) {
        this.configurationService = configurationService;
        this.resultInfoMap = new ConcurrentHashMap<>();
    }

    public ResultModel getResultModelWithZip(String chainTaskId) {
        ResultInfo resultInfo = getResultInfo(chainTaskId);
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

    public void saveResultInfo(String chainTaskId, AvailableReplicateModel replicateModel, String stdout) {
        createStdoutFile(chainTaskId, stdout);

        File zipFile = FileHelper.zipFolder(getResultFolderPath(chainTaskId));
        log.info("Zip file has been created [chainTaskId:{}, zipFile:{}]", chainTaskId, zipFile.getAbsolutePath());

        ResultInfo resultInfo = ResultInfo.builder()
                .image(replicateModel.getAppUri())
                .cmd(replicateModel.getCmd())
                .deterministHash(getDeterministHashFromFile(chainTaskId))
                .datasetUri(replicateModel.getDatasetUri())
                .build();

        resultInfoMap.put(chainTaskId, resultInfo);
    }

    public ResultInfo getResultInfo(String chainTaskId) {
        return resultInfoMap.get(chainTaskId);
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

    public String getResultFolderPath(String chainTaskId) {
        return configurationService.getResultBaseDir() + File.separator + chainTaskId + FileHelper.SLASH_OUTPUT;
    }

    public String getResultZipFilePath(String chainTaskId) {
        return getResultFolderPath(chainTaskId) + ".zip";
    }

    public List<String> getAllChainTaskIdsInResultFolder() {
        File resultsFolder = new File(configurationService.getResultBaseDir());
        String[] chainTaskIdFolders = resultsFolder.list((current, name) -> new File(current, name).isDirectory());

        if (chainTaskIdFolders == null || chainTaskIdFolders.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(chainTaskIdFolders);
    }

    private File createStdoutFile(String chainTaskId, String stdoutContent) {
        log.info("Stdout file added to result folder [chainTaskId:{}]", chainTaskId);
        String filePath = getResultFolderPath(chainTaskId) + File.separator + STDOUT_FILENAME;
        return createFileWithContent(filePath, stdoutContent);
    }

    public String getDeterministHashFromFile(String chainTaskId) {
        String hash = "";
        try {
            String deterministFilePathName = getResultFolderPath(chainTaskId) + FileHelper.SLASH_IEXEC_OUT + File.separator + DETERMINIST_FILE_NAME;
            Path deterministFilePath = Paths.get(deterministFilePathName);

            if (deterministFilePath.toFile().exists()) {
                byte[] content = Files.readAllBytes(deterministFilePath);
                hash = bytesToString(Hash.sha256(content));
                log.info("The determinist file exists and its hash has been computed [chainTaskId:{}, hash:{}]", chainTaskId, hash);
                return hash;
            } else {
                log.info("No determinist file exists [chainTaskId:{}]", chainTaskId);
            }

            String resultFilePathName = getResultZipFilePath(chainTaskId);
            byte[] content = Files.readAllBytes(Paths.get(resultFilePathName));
            hash = bytesToString(Hash.sha256(content));
            log.info("The hash of the result file will be used instead [chainTaskId:{}, hash:{}]", chainTaskId, hash);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Failed to getDeterministHashFromFile [chainTaskId:{}]", chainTaskId);
        }

        return hash;
    }

    public Optional<TeeSignature.Sign> getEnclaveSignatureFromFile(String chainTaskId) {
        String executionEnclaveSignatureFileName = getResultFolderPath(chainTaskId) + FileHelper.SLASH_IEXEC_OUT + File.separator + TEE_ENCLAVE_SIGNATURE_FILE_NAME;
        System.out.println(executionEnclaveSignatureFileName);
        Path executionEnclaveSignatureFilePath = Paths.get(executionEnclaveSignatureFileName);

        if (!executionEnclaveSignatureFilePath.toFile().exists()) {
            log.info("TeeSignature file doesn't exist [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        ObjectMapper mapper = new ObjectMapper();
        TeeSignature teeSignature = null;
        try {
            teeSignature = mapper.readValue(executionEnclaveSignatureFilePath.toFile(), TeeSignature.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (teeSignature == null) {
            log.info("TeeSignature file exits but parsing failed [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        TeeSignature.Sign s = teeSignature.getSign();
        log.info("TeeSignature file exists [chainTaskId:{}, v:{}, r:{}, s:{}]",
                chainTaskId, s.getV(), s.getR(), s.getS());
        return Optional.of(s);
    }
}
