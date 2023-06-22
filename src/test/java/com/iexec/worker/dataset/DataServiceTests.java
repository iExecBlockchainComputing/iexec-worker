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
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DataServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String DATASET_ADDRESS = "0x7293635a7891ceb3368b87e4a23b6ea41b78b962";
    private static final String DATASET_RESOURCE_NAME = "iExec-RLC-RLC-icon.png";
    private static final String HTTP_URI = "https://icons.iconarchive.com/icons/cjdowner/cryptocurrency-flat/512/" + DATASET_RESOURCE_NAME;
    private static final String IPFS_URI = "/ipfs/QmUbh7ugQ9WVprTVYjzrCS4d9cCy73zUz4MMchsrqzzu1w";
    private static final String IEXEC_IPFS_DOWNLOAD = "Try to download dataset from https://ipfs-gateway.v8-bellecour.iex.ec";
    private static final String IO_IPFS_DOWNLOAD = "Try to download dataset from https://gateway.ipfs.io";
    private static final String PINATA_IPFS_DOWNLOAD = "Try to download dataset from https://gateway.pinata.cloud";
    private static final String CHECKSUM = "0x4d8401fd4484f07c202c0a2b9ce6907eabd69efae0cec3956f1a56a6b19a9daa";

    @TempDir
    public File temporaryFolder;

    @Spy
    @InjectMocks
    private DataService dataService;

    @Mock
    private WorkerConfigurationService workerConfigurationService;

    private String iexecIn;

    private TaskDescription.TaskDescriptionBuilder getTaskDescriptionBuilder() {
        return TaskDescription.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .datasetUri(HTTP_URI)
                .datasetChecksum(CHECKSUM)
                .datasetAddress(DATASET_ADDRESS)
                .isTeeTask(false);
    }

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        iexecIn = temporaryFolder.getAbsolutePath();
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(iexecIn);
    }

    @Test
    void shouldDownloadStandardTaskDataset() throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder().build();
        String filepath = dataService.downloadStandardDataset(taskDescription);
        assertThat(filepath).isEqualTo(iexecIn + "/" + DATASET_ADDRESS);
    }

    @Test
    void shouldDownloadStandardDatasetFromIexecGateway(CapturedOutput output) throws WorkflowException {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetUri(IPFS_URI)
                .build();
        final URL resourceFile = this.getClass().getClassLoader().getResource(DATASET_RESOURCE_NAME);
        assertThat(resourceFile).isNotNull();
        when(dataService.downloadFile(anyString(), anyString(), anyString(), anyString())).thenReturn(resourceFile.getFile());
        final String filepath = dataService.downloadStandardDataset(taskDescription);
        assertThat(filepath).isNotEmpty();
        assertThat(output)
                .contains(IEXEC_IPFS_DOWNLOAD)
                .doesNotContain(IO_IPFS_DOWNLOAD)
                .doesNotContain(PINATA_IPFS_DOWNLOAD);
    }

    @Test
    void shouldDownloadStandardDatasetFromIpfsGateway(CapturedOutput output) throws WorkflowException {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetUri(IPFS_URI)
                .build();
        final URL resourceFile = this.getClass().getClassLoader().getResource(DATASET_RESOURCE_NAME);
        assertThat(resourceFile).isNotNull();
        when(dataService.downloadFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("")
                .thenReturn(resourceFile.getFile());
        final String filepath = dataService.downloadStandardDataset(taskDescription);
        assertThat(filepath).isNotEmpty();
        assertThat(output)
                .contains(IEXEC_IPFS_DOWNLOAD)
                .contains(IO_IPFS_DOWNLOAD)
                .doesNotContain(PINATA_IPFS_DOWNLOAD);
    }

    @Test
    void shouldDownloadStandardDatasetFromPinataGateway(CapturedOutput output) throws WorkflowException {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetUri(IPFS_URI)
                .build();
        final URL resourceFile = this.getClass().getClassLoader().getResource(DATASET_RESOURCE_NAME);
        assertThat(resourceFile).isNotNull();
        when(dataService.downloadFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("")
                .thenReturn("")
                .thenReturn(resourceFile.getFile());
        final String filepath = dataService.downloadStandardDataset(taskDescription);
        assertThat(filepath).isNotEmpty();
        assertThat(output)
                .contains(IEXEC_IPFS_DOWNLOAD)
                .contains(IO_IPFS_DOWNLOAD)
                .contains(PINATA_IPFS_DOWNLOAD);
    }

    @Test
    void shouldNotDownloadDatasetWhenFailureOnAllGateways(CapturedOutput output) throws WorkflowException {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetUri(IPFS_URI)
                .build();
        final URL resourceFile = this.getClass().getClassLoader().getResource(DATASET_RESOURCE_NAME);
        assertThat(resourceFile).isNotNull();
        when(dataService.downloadFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("");
        assertThrows(
                WorkflowException.class
                , () -> dataService.downloadStandardDataset(taskDescription)
        );
        assertThat(output)
                .contains(IEXEC_IPFS_DOWNLOAD)
                .contains(IO_IPFS_DOWNLOAD)
                .contains(PINATA_IPFS_DOWNLOAD);
    }

    @Test
    void shouldNotDownloadDatasetSinceEmptyChainTaskId() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .chainTaskId("")
                .build();
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldNotDownloadDatasetSinceEmptyUri() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetUri("")
                .build();
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test

    void shouldNotDownloadDatasetSinceEmptyDatasetAddress() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetAddress("")
                .build();
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldNotDownloadDatasetSinceEmptyParentDirectory() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder().build();
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldNotDownloadDatasetSinceBadChecksum() {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetChecksum("badChecksum")
                .build();
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_BAD_CHECKSUM);
    }

    @Test
    void shouldDownloadDatasetSinceEmptyOnchainChecksum() throws Exception {
        final TaskDescription taskDescription = getTaskDescriptionBuilder()
                .datasetChecksum("")
                .build();
        assertThat(dataService.downloadStandardDataset(taskDescription))
                .isEqualTo(iexecIn + "/" + DATASET_ADDRESS);
    }

    @Test
    void shouldDownloadInputFiles() throws Exception {
        List<String> uris = List.of(HTTP_URI);
        dataService.downloadStandardInputFiles(CHAIN_TASK_ID, uris);
        File inputFile = new File(iexecIn, "iExec-RLC-RLC-icon.png");
        assertThat(inputFile).exists();
    }
}
