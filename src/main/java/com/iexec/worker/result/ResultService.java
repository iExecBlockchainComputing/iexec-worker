package com.iexec.worker.result;

import com.iexec.common.result.ResultModel;
import com.iexec.worker.utils.WorkerConfigurationService;
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

    public ResultModel getResultModelWithZip(String taskId) {
        MetadataResult metadataResult = getMetaDataResult(taskId);
        byte[] zipResultAsBytes = new byte[0];
        String zipLocation = configurationService.getResultBaseDir() + "/" + taskId + ".zip";
        try {
            zipResultAsBytes = Files.readAllBytes(Paths.get(zipLocation));
        } catch (IOException e) {
            log.error("Failed to get zip result [taskId:{}, zipLocation:{}]", taskId, zipLocation);
        }

        return ResultModel.builder()
                .taskId(taskId)
                .image(metadataResult.getImage())
                .cmd(metadataResult.getCmd())
                .zip(zipResultAsBytes).build();
    }

    public void addMetaDataResult(String taskId, MetadataResult metadataResult) {
        metadataResultMap.put(taskId, metadataResult);
    }

    public MetadataResult getMetaDataResult(String taskId) {
        return metadataResultMap.get(taskId);
    }

}
