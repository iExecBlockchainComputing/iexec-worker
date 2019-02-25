package com.iexec.worker.dataset;

import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.FileHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
public class DatasetService {


    private final WorkerConfigurationService configurationService;

    public DatasetService(WorkerConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /*
     * In order to keep a linear replicate workflow, we'll always have the steps:
     * APP_DOWNLOADING, ..., DATA_DOWNLOADING, ..., COMPUTING (even when the dataset requested is 0x0).
     * In the 0x0 dataset case, we'll have an empty datasetUri, and we'll consider the dataset as downloaded
     */
    public boolean downloadDataset(String chainTaskId, String datasetUri) {
        if (chainTaskId.isEmpty()) {
            log.error("Failed to downloadDataset, chainTaskId shouldn't be empty [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, datasetUri);
            return false;
        }
        if (datasetUri.isEmpty()) {
            log.info("There's nothing to download for this task [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, datasetUri);
            return true;
        }
        return FileHelper.downloadFileInDirectory(datasetUri, getDatasetFolderPath(chainTaskId));
    }

    public String getDatasetFolderPath(String chainTaskId) {
        return configurationService.getResultBaseDir() + File.separator + chainTaskId + FileHelper.SLASH_INPUT;
    }


}
