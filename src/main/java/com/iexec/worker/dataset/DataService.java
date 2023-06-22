/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.utils.FileHashUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.MultiAddressHelper;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.WorkflowException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;


@Slf4j
@Service
public class DataService {

    private final WorkerConfigurationService workerConfigurationService;

    public DataService(WorkerConfigurationService workerConfigurationService) {
        this.workerConfigurationService = workerConfigurationService;
    }

    /**
     * Download dataset file for the given standard task and save
     * it in {@link IexecFileHelper#SLASH_IEXEC_IN}.
     * 
     * @param taskDescription
     * @return downloaded dataset file path
     * @throws WorkflowException if download fails or bad checksum.
     */
    public String downloadStandardDataset(@Nonnull TaskDescription taskDescription)
            throws WorkflowException {
        String chainTaskId = taskDescription.getChainTaskId();
        String uri = taskDescription.getDatasetUri();
        String filename = taskDescription.getDatasetAddress();
        String parentDirectoryPath = workerConfigurationService.getTaskInputDir(chainTaskId);
        String datasetLocalFilePath = "";
        if (MultiAddressHelper.isMultiAddress(uri)) {
            for (String ipfsGateway : MultiAddressHelper.IPFS_GATEWAYS) {
                log.debug("Try to download dataset from {}", ipfsGateway);
                datasetLocalFilePath =
                        downloadFile(chainTaskId, ipfsGateway + uri, parentDirectoryPath, filename);
                if (!datasetLocalFilePath.isEmpty()) {
                    break;
                }
            }
        } else {
            datasetLocalFilePath =
                    downloadFile(chainTaskId, uri, parentDirectoryPath, filename);
        }
        if (datasetLocalFilePath.isEmpty()) {
            throw new WorkflowException(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
        }
        String expectedSha256 = taskDescription.getDatasetChecksum();
        if (StringUtils.isEmpty(expectedSha256)) {
            log.warn("INSECURE! Cannot check empty on-chain dataset checksum " +
                    "[chainTaskId:{}]", chainTaskId);
            return datasetLocalFilePath;
        }
        String actualSha256 = FileHashUtils.sha256(new File(datasetLocalFilePath));
        if (!expectedSha256.equals(actualSha256)) {
            log.error("Dataset checksum mismatch [chainTaskId:{}, " +
                    "expected:{}, actual:{}]", chainTaskId, expectedSha256,
                    actualSha256);
            throw new WorkflowException(ReplicateStatusCause.DATASET_FILE_BAD_CHECKSUM);
        }
        return datasetLocalFilePath;
    }

    /**
     * Download input files for the given standard task and save them
     * in the input folder.
     * 
     * @param chainTaskId
     * @param uriList
     * @throws WorkflowException if download fails.
     */
    public void downloadStandardInputFiles(String chainTaskId, @Nonnull List<String> uriList)
            throws WorkflowException {
        for (String uri: uriList) {
            String filename = !StringUtils.isEmpty(uri)
                    ? Paths.get(uri).getFileName().toString()
                    : "";
            String parenDirectoryPath = workerConfigurationService.getTaskInputDir(chainTaskId);
            if (downloadFile(chainTaskId, uri, parenDirectoryPath, filename).isEmpty()) {
                throw new WorkflowException(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED);
            }
        }
    }

    /**
     * Download a file from a URI in the provided parent
     * directory and save it with the provided filename.
     * 
     * @param chainTaskId
     * @param uri
     * @param parentDirectoryPath
     * @param filename
     * @return absolute path of the saved file
     */
    String downloadFile(String chainTaskId, String uri,
                        String parentDirectoryPath, String filename) {
        if (StringUtils.isEmpty(chainTaskId) ||
                StringUtils.isEmpty(uri) ||
                StringUtils.isEmpty(parentDirectoryPath) ||
                StringUtils.isEmpty(filename)) {
            log.error("Failed to download, args shouldn't be empty " +
                    "[chainTaskId:{}, datasetUri:{}, parentDir:{}, filename:{}]",
                    chainTaskId, uri, parentDirectoryPath, filename);
            return StringUtils.EMPTY;
        }
        return FileHelper.downloadFile(uri, parentDirectoryPath, filename);
    }
}
