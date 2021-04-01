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

import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.when;

public class DataServiceTest {

    public static final String CHAIN_TASK_ID = "chainTaskId";
    public static final String URI =
            "https://icons.iconarchive.com/icons/cjdowner/cryptocurrency-flat/512/iExec-RLC-RLC-icon.png";
    public static final String FILENAME = "icon.png";
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @InjectMocks
    private DataService dataService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    private String tmp;

    @Before
    public void beforeEach() throws IOException {
        MockitoAnnotations.openMocks(this);
        tmp = temporaryFolder.newFolder().getAbsolutePath();
        when(workerConfigurationService.getTaskInputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
    }

    @Test
    public void shouldDownloadFile() {
        Assertions.assertEquals(tmp + "/" + FILENAME,
                dataService.downloadFile(CHAIN_TASK_ID, URI, FILENAME));
    }

    @Test
    public void shouldNotDownloadFileSinceEmptyChainTaskId() {
        Assertions.assertTrue(dataService.downloadFile("",
                URI,
                FILENAME).isEmpty());
    }

    @Test
    public void shouldNotDownloadFileSinceEmptyUri() {
        Assertions.assertTrue(dataService.downloadFile(CHAIN_TASK_ID,
                "",
                FILENAME).isEmpty());
    }

    @Test
    public void shouldNotDownloadFileSinceEmptyFilename() {
        Assertions.assertTrue(dataService.downloadFile(CHAIN_TASK_ID,
                URI,
                "").isEmpty());
    }

    @Test
    public void shouldNotDownloadFilesSinceNoUri() {
        Assertions.assertFalse(dataService.downloadFiles(CHAIN_TASK_ID,
                Collections.singletonList("")));
    }
}