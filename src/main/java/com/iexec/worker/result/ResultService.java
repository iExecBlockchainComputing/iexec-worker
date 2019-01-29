package com.iexec.worker.result;

import com.iexec.common.result.ResultModel;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ResultService {

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

    public void addResultInfo(String chainTaskId, ResultInfo resultInfo) {
        resultInfoMap.put(chainTaskId, resultInfo);
    }

    public ResultInfo getResultInfo(String chainTaskId) {
        return resultInfoMap.get(chainTaskId);
    }

    public boolean removeResult(String chainTaskId) {
        boolean deletedInMap = resultInfoMap.remove(chainTaskId) != null;
        boolean deletedZipFile = FileHelper.deleteFile(getResultZipFilePath(chainTaskId));
        boolean deletedResultFolder = FileHelper.deleteFolder(getResultFolderPath(chainTaskId));

        boolean ret = deletedInMap && deletedZipFile && deletedResultFolder;
        if (ret) {
            log.info("The result of the chainTaskId has been deleted [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("The result of the chainTaskId couldn't be deleted [chainTaskId:{}, deletedInMap:{}, " +
                            "deletedZipFile:{}, deletedResultFolder:{}]",
                    chainTaskId, deletedInMap, deletedZipFile, deletedResultFolder);
        }

        return ret;
    }

    public String getResultFolderPath(String chainTaskId){
        return configurationService.getResultBaseDir() + File.separator + chainTaskId;
    }

    public String getResultZipFilePath(String chainTaskId){
        return configurationService.getResultBaseDir() + File.separator + chainTaskId + ".zip";
    }

    public List<String> getAllChainTaskIdInResultFolder(){
        File resultsFolder = new File(configurationService.getResultBaseDir());
        String[] chainTaskIdFolders = resultsFolder.list((current, name) -> new File(current, name).isDirectory());
        return Arrays.asList(chainTaskIdFolders);
    }
}
