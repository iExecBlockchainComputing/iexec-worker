package com.iexec.worker.result;

import com.iexec.common.result.ResultModel;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ResultService {

    private Map<String, MetadataResult> metadataResultMap;
    private WorkerConfigurationService configurationService;

    public ResultService(WorkerConfigurationService configurationService) {
        this.configurationService = configurationService;
        this.metadataResultMap = new ConcurrentHashMap<>();
    }

    public ResultModel getResultModelWithZip(String chainTaskId) {
        MetadataResult metadataResult = getMetaDataResult(chainTaskId);
        byte[] zipResultAsBytes = new byte[0];
        String zipLocation = configurationService.getResultBaseDir() + "/" + chainTaskId + ".zip";
        try {
            zipResultAsBytes = Files.readAllBytes(Paths.get(zipLocation));
        } catch (IOException e) {
            log.error("Failed to get zip result [chainTaskId:{}, zipLocation:{}]", chainTaskId, zipLocation);
        }

        return ResultModel.builder()
                .chainTaskId(chainTaskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .zip(zipResultAsBytes)
                .deterministHash(metadataResult.getDeterministHash())
                .build();
    }

    public void addMetaDataResult(String chainTaskId, MetadataResult metadataResult) {
        metadataResultMap.put(chainTaskId, metadataResult);
    }

    public MetadataResult getMetaDataResult(String chainTaskId) {
        return metadataResultMap.get(chainTaskId);
    }

    public boolean removeResult(String chainTaskId) {
        boolean deletedInMap = metadataResultMap.remove(chainTaskId) != null;
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
        return configurationService.getResultBaseDir() + "/" + chainTaskId;
    }

    public String getResultZipFilePath(String chainTaskId){
        return configurationService.getResultBaseDir() + "/" + chainTaskId + ".zip";
    }
}
