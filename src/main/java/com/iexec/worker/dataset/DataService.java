/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.worker.dataset;

import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.HashUtils;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;


@Slf4j
@Service
public class DataService {

    @Value("${decryptFilePath}")
    private String scriptFilePath;

    private final WorkerConfigurationService workerConfigurationService;

    public DataService(WorkerConfigurationService workerConfigurationService) {
        this.workerConfigurationService = workerConfigurationService;
    }

    /*
     * In order to keep a linear replicate workflow, we'll always have the steps:
     * APP_DOWNLOADING, ..., DATA_DOWNLOADING, ..., COMPUTING (even when the dataset requested is 0x0).
     * In the 0x0 dataset case, we'll have an empty uri, and we'll consider the dataset as downloaded
     */
    public String downloadFile(String chainTaskId, String uri) {
        if (chainTaskId.isEmpty()) {
            log.error("Failed to download, chainTaskId shouldn't be empty [chainTaskId:{}, datasetUri:{}]",
                    chainTaskId, uri);
            return "";
        }
        if (uri.isEmpty()) {
            log.info("There's nothing to download for this task [chainTaskId:{}, uri:{}]",
                    chainTaskId, uri);
            return "";
        }
        return FileHelper.downloadFile(uri, workerConfigurationService.getTaskInputDir(chainTaskId));
    }

    public boolean downloadFiles(String chainTaskId, List<String> uris) {
        for (String uri:uris){
            if (downloadFile(chainTaskId, uri).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean unzipDownloadedTeeDataset(String chainTaskId, String datasetUri) {
        if (datasetUri.isEmpty()){
            log.info("Failed to unzipDownloadedTeeDataset (empty datasetUri) [chainTaskId:{}, datasetUri:{}]", chainTaskId, datasetUri);
            return false;
        }
        String datasetFilename = Paths.get(datasetUri).getFileName().toString();
        String taskInputDirPath = workerConfigurationService.getTaskInputDir(chainTaskId);
        return FileHelper.unZipFile(taskInputDirPath + "/" + datasetFilename, taskInputDirPath);
    }

    public boolean isDatasetDecryptionNeeded(String chainTaskId) {
        String datasetSecretFilePath = workerConfigurationService.getDatasetSecretFilePath(chainTaskId);

        if (!new File(datasetSecretFilePath).exists()) {
            log.info("No dataset secret file found, will continue without decrypting dataset [chainTaskId:{}]", chainTaskId);
            return false;
        }

        return true;
    }

    public boolean decryptDataset(String chainTaskId, String datasetUri) {
        String datasetFileName = Paths.get(datasetUri).getFileName().toString();
        String datasetFilePath = workerConfigurationService.getTaskInputDir(chainTaskId) + File.separator + datasetFileName;
        String datasetSecretFilePath = workerConfigurationService.getDatasetSecretFilePath(chainTaskId);

        log.info("Decrypting dataset file [datasetFile:{}, secretFile:{}]", datasetFilePath, datasetSecretFilePath);

        decryptFile(datasetFilePath, datasetSecretFilePath);

        String decryptedDatasetFilePath = datasetFilePath + ".recovered";

        if (!new File(decryptedDatasetFilePath).exists()) {
            log.error("Decrypted dataset file not found [chainTaskId:{}, decryptedDatasetFilePath:{}]",
                    chainTaskId, decryptedDatasetFilePath);
            return false;
        }

        // replace original dataset file with decrypted one
        return FileHelper.replaceFile(datasetFilePath, decryptedDatasetFilePath);
    }

    private void decryptFile(String dataFilePath, String secretFilePath) {
        // TODO decrypt file with java code
        throw new UnsupportedOperationException("Cannot decrypt file with bash script");
        // ProcessBuilder pb = new ProcessBuilder(this.scriptFilePath, dataFilePath, secretFilePath);

        // try {
        //     Process pr = pb.start();

        //     BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        //     String line;

        //     while ((line = in.readLine()) != null) { log.info(line); }

        //     pr.waitFor();
        //     in.close();
        // } catch (Exception e) {
        //     log.error("Error while trying to decrypt data [datasetFile{}, secretFile:{}]",
        //             dataFilePath, secretFilePath);
        //     e.printStackTrace();
        // }
    }

    /**
     * Compute sha256 of a file and check if it matches the expected value
     * @param expectedSha256 expected sha256 value
     * @param filePathToCheck file path to check
     * @return true if sha256 values are the same
     */
    public boolean hasExpectedSha256(String expectedSha256, String filePathToCheck) {
        return HashUtils.getFileSha256(filePathToCheck).equals(expectedSha256);
    }
}