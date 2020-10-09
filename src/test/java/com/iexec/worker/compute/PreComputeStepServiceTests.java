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

package com.iexec.worker.compute;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.sms.secret.TaskSecrets;
import com.iexec.common.task.TaskDescription;
import com.iexec.worker.config.WorkerConfigurationService;
import com.iexec.worker.dataset.DataService;
import com.iexec.worker.docker.DockerService;
import com.iexec.worker.sms.SmsService;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class PreComputeStepServiceTests {

    private final String chainTaskId = "chainTaskId";
    private final String datasetUri = "datasetUri";
    private final String datasetSecretFilePath = "datasetSecretFilePath";
    private final String beneficiarySecretFilePath =
            "beneficiarySecretFilePath";
    private final String enclaveSecretFilePath = "enclaveSecretFilePath";
    private final TaskDescription taskDescription = TaskDescription.builder()
            .chainTaskId(chainTaskId)
            .datasetUri(datasetUri)
            .build();
    private final WorkerpoolAuthorization workerpoolAuthorization =
            WorkerpoolAuthorization.builder().build();

    @InjectMocks
    private PreComputeStepService preComputeStepService;
    @Mock
    private SmsService smsService;
    @Mock
    private DataService dataService;
    @Mock
    private WorkerConfigurationService workerConfigService;
    @Mock
    private DockerService dockerService;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Standard pre compute
     */

    // Standard pre compute without secret
    @Test
    public void shouldRunStandardPreCompute() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(false);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization)).isTrue();
        verify(dataService, times(0)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldRunStandardPreComputeWithDatasetDecryption() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(true);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization)).isTrue();
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldNotRunStandardPreComputeWithDatasetDecryptionSinceCantDecrypt() {
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.empty());
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(false);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization)).isFalse();
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    // Standard pre compute with secret

    @Test
    public void shouldRunStandardPreComputeWithSecrets() {
        TaskSecrets taskSecrets = TaskSecrets.builder().build();
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.of(taskSecrets));
        when(workerConfigService.getDatasetSecretFilePath(chainTaskId)).thenReturn(datasetSecretFilePath);
        when(workerConfigService.getBeneficiarySecretFilePath(chainTaskId)).thenReturn(beneficiarySecretFilePath);
        when(workerConfigService.getEnclaveSecretFilePath(chainTaskId)).thenReturn(enclaveSecretFilePath);
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(false);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization))
                .isTrue();
        verify(smsService, times(1)).saveSecrets(chainTaskId, taskSecrets,
                datasetSecretFilePath,
                beneficiarySecretFilePath, enclaveSecretFilePath);
        verify(dataService, times(0)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldRunStandardPreComputeWithSecretsAndDatasetDecryption() {
        TaskSecrets taskSecrets = TaskSecrets.builder().build();
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.of(taskSecrets));
        when(workerConfigService.getDatasetSecretFilePath(chainTaskId)).thenReturn(datasetSecretFilePath);
        when(workerConfigService.getBeneficiarySecretFilePath(chainTaskId)).thenReturn(beneficiarySecretFilePath);
        when(workerConfigService.getEnclaveSecretFilePath(chainTaskId)).thenReturn(enclaveSecretFilePath);
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(true);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization))
                .isTrue();
        verify(smsService, times(1)).saveSecrets(chainTaskId, taskSecrets,
                datasetSecretFilePath,
                beneficiarySecretFilePath, enclaveSecretFilePath);
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    @Test
    public void shouldNotRunStandardPreComputeWithSecretsAndDatasetDecryptionSinceCantDecrypt() {
        TaskSecrets taskSecrets = TaskSecrets.builder().build();
        when(smsService.fetchTaskSecrets(workerpoolAuthorization)).thenReturn(Optional.of(taskSecrets));
        when(workerConfigService.getDatasetSecretFilePath(chainTaskId)).thenReturn(datasetSecretFilePath);
        when(workerConfigService.getBeneficiarySecretFilePath(chainTaskId)).thenReturn(beneficiarySecretFilePath);
        when(workerConfigService.getEnclaveSecretFilePath(chainTaskId)).thenReturn(enclaveSecretFilePath);
        when(dataService.isDatasetDecryptionNeeded(chainTaskId)).thenReturn(true);
        when(dataService.decryptDataset(chainTaskId,
                taskDescription.getDatasetUri())).thenReturn(false);

        Assertions.assertThat(preComputeStepService.runStandardPreCompute(taskDescription, workerpoolAuthorization))
                .isFalse();
        verify(smsService, times(1)).saveSecrets(chainTaskId, taskSecrets,
                datasetSecretFilePath,
                beneficiarySecretFilePath, enclaveSecretFilePath);
        verify(dataService, times(1)).decryptDataset(chainTaskId, datasetUri);
    }

    /**
     * Tee pre compute
     */

    @Test
    public void shouldRunTeePreCompute() {
        taskDescription.setTeePostComputeImage("teePostComputeImage");
        when(dockerService.pullImage(taskDescription.getTeePostComputeImage())).thenReturn(true);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);

        Assertions.assertThat(preComputeStepService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEqualTo(secureSessionId);
    }

    @Test
    public void shouldNotRunTeePreComputeSinceCantPullPreComputeImage() {
        taskDescription.setTeePostComputeImage("teePostComputeImage");
        when(dockerService.pullImage(taskDescription.getTeePostComputeImage())).thenReturn(false);
        String secureSessionId = "secureSessionId";
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn(secureSessionId);

        Assertions.assertThat(preComputeStepService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
    }

    @Test
    public void shouldNotRunTeePreComputeSinceCantCreateTeeSession() {
        taskDescription.setTeePostComputeImage("teePostComputeImage");
        when(dockerService.pullImage(taskDescription.getTeePostComputeImage())).thenReturn(true);
        when(smsService.createTeeSession(workerpoolAuthorization)).thenReturn("");

        Assertions.assertThat(preComputeStepService.runTeePreCompute(taskDescription, workerpoolAuthorization))
                .isEmpty();
    }

}