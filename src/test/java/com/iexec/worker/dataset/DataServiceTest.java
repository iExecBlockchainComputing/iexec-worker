/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.utils.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class DataServiceTest {

    public static final String CHAIN_TASK_ID = "chainTaskId";
    public static final String URI =
            "https://icons.iconarchive.com/icons/cjdowner/cryptocurrency-flat/512/iExec-RLC-RLC-icon.png";
    public static final String FILENAME = "icon.png";
    public static final String CHECKSUM =
            "0x240987ee1480e8e0b1b26fa806810fea04021191a8e6d8ab6325c15fa61fa9b6";

    @TempDir
    public File temporaryFolder;

    @InjectMocks
    private DataService dataService;

    @Mock
    private WorkerConfigurationService workerConfigurationService;

    private String iexecIn;

    private TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .datasetUri(URI)
            .datasetName(FILENAME)
            .datasetChecksum(CHECKSUM)
            .isTeeTask(false)
            .build();

    @BeforeEach
    public void beforeEach() throws IOException {
        MockitoAnnotations.openMocks(this);
        iexecIn = temporaryFolder.getAbsolutePath();
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(iexecIn);
    }

    @Test
    public void shouldDownloadStandardTaskDataset() throws Exception {
        String filepath = dataService.downloadStandardDataset(taskDescription);
        assertThat(filepath).isEqualTo(iexecIn + "/" + FILENAME);
    }


    @Test
    public void shouldNotDownloadDatasetSinceEmptyChainTaskId() throws Exception {
        taskDescription.setChainTaskId("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldNotDownloadDatasetSinceEmptyUri() throws Exception {
        taskDescription.setDatasetUri("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldNotDownloadDatasetSinceEmptyFilename() throws Exception {
        taskDescription.setDatasetName("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldNotDownloadDatasetSinceEmptyParentDirectory() throws Exception {
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID)).thenReturn("");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldNotDownloadDatasetSinceBadChecksum() throws Exception {
        taskDescription.setDatasetChecksum("badChecksum");
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardDataset(taskDescription));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.DATASET_FILE_BAD_CHECKSUM);
    }

    @Test
    public void shouldDownloadDatasetSinceEmptyOnchainChecksum() throws Exception {
        taskDescription.setDatasetChecksum("");
        assertThat(dataService.downloadStandardDataset(taskDescription))
                .isEqualTo(iexecIn + "/" + FILENAME);
    }

    @Test
    public void shouldDownloadInputFiles() throws Exception {
        List<String> uris = List.of(URI);
        dataService.downloadStandardInputFiles(CHAIN_TASK_ID, uris);
        File inputFile = new File(iexecIn, "iExec-RLC-RLC-icon.png");
        assertThat(inputFile).exists();
    }

    @Test
    public void shouldNotDownloadInputFilesSinceNoUriList() throws Exception {
        WorkflowException e = assertThrows(
                WorkflowException.class,
                () -> dataService.downloadStandardInputFiles(CHAIN_TASK_ID, null));
        assertThat(e.getReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED);

    }
}