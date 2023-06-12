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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class DataServiceTests {

    public static final String CHAIN_TASK_ID = "chainTaskId";
    public static final String URI =
            "https://icons.iconarchive.com/icons/cjdowner/cryptocurrency-flat/512/iExec-RLC-RLC-icon.png";
    public static final String DATASET_ADDRESS = "0x7293635a7891ceb3368b87e4a23b6ea41b78b962";
    public static final String CHECKSUM =
            "0x4d8401fd4484f07c202c0a2b9ce6907eabd69efae0cec3956f1a56a6b19a9daa";

    @TempDir
    public File temporaryFolder;

    @InjectMocks
    private DataService dataService;

    @Mock
    private WorkerConfigurationService workerConfigurationService;

    private String iexecIn;

    private final TaskDescription.TaskDescriptionBuilder taskDescriptionBuilder = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .datasetUri(URI)
            .datasetChecksum(CHECKSUM)
            .datasetAddress(DATASET_ADDRESS)
            .isTeeTask(false);

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
        iexecIn = temporaryFolder.getAbsolutePath();
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(iexecIn);
    }

    @Test
    void shouldDownloadStandardTaskDataset() throws Exception {
        String filepath = dataService.downloadStandardDataset(taskDescriptionBuilder.build());
        assertThat(filepath).isEqualTo(iexecIn + "/" + DATASET_ADDRESS);
    }


    @Test
    void shouldNotDownloadDatasetSinceEmptyChainTaskId() {
        TaskDescription taskDescription = taskDescriptionBuilder
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
        TaskDescription taskDescription = taskDescriptionBuilder
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
        TaskDescription taskDescription = taskDescriptionBuilder
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
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescriptionBuilder.build()));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    void shouldNotDownloadDatasetSinceBadChecksum() {
        TaskDescription taskDescription = taskDescriptionBuilder
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
        TaskDescription taskDescription = taskDescriptionBuilder
                .datasetChecksum("")
                .build();
        assertThat(dataService.downloadStandardDataset(taskDescription))
                .isEqualTo(iexecIn + "/" + DATASET_ADDRESS);
    }

    @Test
    void shouldDownloadInputFiles() throws Exception {
        List<String> uris = List.of(URI);
        dataService.downloadStandardInputFiles(CHAIN_TASK_ID, uris);
        File inputFile = new File(iexecIn, "iExec-RLC-RLC-icon.png");
        assertThat(inputFile).exists();
    }
}
