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

package com.iexec.worker.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.chain.eip712.EIP712Domain;
import com.iexec.common.chain.eip712.entity.EIP712Challenge;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.resultproxy.api.ResultProxyClient;
import com.iexec.worker.chain.CredentialsService;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.BlockchainAdapterConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.iexec.common.chain.DealParams.DROPBOX_RESULT_STORAGE_PROVIDER;
import static com.iexec.common.chain.DealParams.IPFS_RESULT_STORAGE_PROVIDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Slf4j
class ResultServiceTests {

    public static final String RESULT_DIGEST = "0x0000000000000000000000000000000000000000000000000000000000000001";
    // 32 + 32 + 1 = 65 bytes
    public static final String ENCLAVE_SIGNATURE = "0x000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b0c";
    private static final String CHAIN_TASK_ID = "taskId";
    private static final String CHAIN_TASK_ID_2 = "taskId2";
    private static final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker/";
    private static final String CALLBACK = "0x0000000000000000000000000000000000000abc";

    @TempDir
    public File folderRule;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ResultProxyClient resultProxyClient;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private BlockchainAdapterConfigurationService blockchainAdapterConfigurationService;
    @Mock
    private CredentialsService credentialsService;

    @InjectMocks
    private ResultService resultService;
    private String tmp;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        tmp = folderRule.getAbsolutePath();
    }

    @Test
    void shouldWriteErrorToIexecOut() {
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED);

        assertThat(isErrorWritten).isTrue();
        String errorFileAsString = FileHelper.readFile(tmp + "/"
                + ResultService.ERROR_FILENAME);
        assertThat(errorFileAsString).contains("[IEXEC] Error occurred while " +
                "computing the task");
        String computedFileAsString = FileHelper.readFile(tmp + "/"
                + IexecFileHelper.COMPUTED_JSON);
        assertThat(computedFileAsString).isEqualTo("{" +
                "\"deterministic-output-path\":\"/iexec_out/error.txt\"," +
                "\"callback-data\":null," +
                "\"task-id\":null," +
                "\"result-digest\":null," +
                "\"enclave-signature\":null" +
                "}");
    }

    @Test
    void shouldNotWriteErrorToIexecOutSince() {
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn("/null");

        boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED);

        assertThat(isErrorWritten).isFalse();
    }

    @Test
    void shouldGetTeeWeb2ResultLinkSinceIpfs() {
        String storage = IPFS_RESULT_STORAGE_PROVIDER;
        String ipfsHash = "QmcipfsHash";

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());
        when(resultProxyClient.getIpfsHashForTask(CHAIN_TASK_ID)).thenReturn(ipfsHash);

        String resultLink = resultService.getWeb2ResultLink(CHAIN_TASK_ID);

        assertThat(resultLink).isEqualTo(resultService.buildResultLink(storage, "/ipfs/" + ipfsHash));
    }

    @Test
    void shouldGetTeeWeb2ResultLinkSinceDropbox() {
        String storage = DROPBOX_RESULT_STORAGE_PROVIDER;

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());

        String resultLink = resultService.getWeb2ResultLink(CHAIN_TASK_ID);

        assertThat(resultLink).isEqualTo(resultService.buildResultLink(storage, "/results/" + CHAIN_TASK_ID));
    }

    @Test
    void shouldNotGetTeeWeb2ResultLinkSinceBadStorage() {
        String storage = "some-unsupported-third-party-storage";

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().resultStorageProvider(storage).build());

        String resultLink = resultService.getWeb2ResultLink(CHAIN_TASK_ID);

        assertThat(resultLink).isEmpty();
    }


    @Test
    void shouldNotGetTeeWeb2ResultLinkSinceNoTask() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(null);

        String resultLink = resultService.getWeb2ResultLink(CHAIN_TASK_ID);

        assertThat(resultLink).isEmpty();
    }

    //region getComputedFile
    @Test
    void shouldGetComputedFileWithWeb2ResultDigestSinceFile() {
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0x09b727883db89fa3b3504f83e0c67d04a0d4fc35a9670cc4517c49d2a27ad171");
    }

    @Test
    void shouldGetComputedFileWithWeb2ResultDigestSinceFileTree() {
        String chainTaskId = "deterministic-output-directory";

        when(workerConfigurationService.getTaskIexecOutDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output/iexec_out");
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        log.info(hash);
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0xc6114778cc5c33db5fbbd4d0f9be116ed0232961045341714aba5a72d3ef7402");
    }

    @Test
    void shouldGetComputedFileWithWeb3ResultDigest() {
        String chainTaskId = "callback-directory";

        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(CALLBACK).build());

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        log.info(hash);
        // should be equal to the content of the file since it is a byte32
        Assertions.assertThat(hash).isEqualTo(
                "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6");
    }

    @Test
    void shouldNotGetComputedFileWhenFileNotFound() {
        String chainTaskId = "does-not-exist";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        Assertions.assertThat(computedFile).isNull();
    }

    @Test
    void shouldNotComputeWeb2ResultDigestWhenFileTreeNotFound() {
        String chainTaskId = "deterministic-output-directory-missing";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        Assertions.assertThat(computedFile).isNull();
        computedFile = resultService.readComputedFile(chainTaskId);
        Assertions.assertThat(computedFile).isNotNull();
        String resultDigest = resultService.computeResultDigest(computedFile);
        Assertions.assertThat(resultDigest).isEmpty();
    }

    @Test
    void shouldNotComputeWeb3ResultDigestWhenNoCallbackData() {
        String chainTaskId = "callback-no-data";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(CALLBACK).build());
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        Assertions.assertThat(computedFile).isNull();
        computedFile = resultService.readComputedFile(chainTaskId);
        Assertions.assertThat(computedFile).isNotNull();
        String resultDigest = resultService.computeResultDigest(computedFile);
        Assertions.assertThat(resultDigest).isEmpty();
    }
    //endregion

    //region writeComputedFile
    @Test
    void shouldWriteComputedFile() throws JsonProcessingException {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isTrue();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        ComputedFile writtenComputeFile = new ObjectMapper()
                .readValue(writtenComputeFileAsString, ComputedFile.class);
        Assertions.assertThat(writtenComputeFile).isEqualTo(computedFile);
    }

    @Test
    void shouldNotWriteComputedFileSinceNothingToWrite() {
        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(null);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceNoChainTaskId() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId("")
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceNotActive() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.UNSET).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceAlreadyWritten() throws JsonProcessingException {
        ComputedFile newComputedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("0x0000000000000000000000000000000000000000000000000000000000000003")
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        //mock old file already written
        resultService.writeComputedFile(ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build());
        //write new file
        boolean isWritten = resultService.writeComputedFile(newComputedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        ComputedFile writtenComputeFile = new ObjectMapper()
                .readValue(writtenComputeFileAsString, ComputedFile.class);
        Assertions.assertThat(writtenComputeFile).isNotEqualTo(newComputedFile);
    }

    @Test
    void shouldNotWriteComputedFileSinceResultDigestIsEmpty() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("")
                .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceResultDigestIsInvalid() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("0x01")
                .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceSignatureIsRequiredAndEmpty() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature("")
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceSignatureIsRequiredAndInvalid() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature("0x01")
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        Assertions.assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceWriteFailed() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder()
                        .status(ChainTaskStatus.ACTIVE).build()));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(":somewhere");
        when(iexecHubService.isTeeTask(CHAIN_TASK_ID)).thenReturn(true);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        Assertions.assertThat(isWritten).isFalse();
    }
    //endregion

    //region getIexecUploadToken
    @Test
    void shouldGetIexecUploadToken() {
        final int chainId = 1;
        final EIP712Domain domain = spy(new EIP712Domain("iExec Result Repository", "1", chainId, null));
        final EIP712Challenge challenge = spy(new EIP712Challenge(domain, null));
        final String signedChallenge = "signedChallenge";
        final String uploadToken = "uploadToken";

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(chainId);
        when(resultProxyClient.getChallenge(chainId)).thenReturn(challenge);
        when(credentialsService.signEIP712EntityAndBuildToken(challenge)).thenReturn(signedChallenge);
        when(resultProxyClient.login(chainId, signedChallenge)).thenReturn(uploadToken);

        assertThat(resultService.getIexecUploadToken()).isEqualTo(uploadToken);

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(chainId);
        verify(credentialsService, times(1)).signEIP712EntityAndBuildToken(challenge);
        verify(resultProxyClient, times(1)).login(chainId, signedChallenge);

        verify(challenge, times(1)).getDomain();
        verify(domain, times(1)).getName();
        verify(domain, times(1)).getChainId();
    }

    @Test
    void shouldNotGetIexecUploadTokenSinceNoResultChallenge() {
        final int chainId = 1;

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(chainId);
        when(resultProxyClient.getChallenge(chainId)).thenReturn(null);

        assertThat(resultService.getIexecUploadToken()).isEmpty();

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(chainId);
        verify(credentialsService, never()).signEIP712EntityAndBuildToken(any());
        verify(resultProxyClient, never()).login(anyInt(), any());
    }

    @Test
    void shouldNotGetIexecUploadTokenSinceGetResultChallengeThrows() {
        final int chainId = 1;

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(chainId);
        when(resultProxyClient.getChallenge(chainId)).thenThrow(RuntimeException.class);

        assertThat(resultService.getIexecUploadToken()).isEmpty();

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(chainId);
        verify(credentialsService, never()).signEIP712EntityAndBuildToken(any());
        verify(resultProxyClient, never()).login(anyInt(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "wrong name"})
    void shouldNotGetIexecUploadTokenSinceWrongDomainName(String wrongDomainName) {
        final int chainId = 1;
        final EIP712Domain domain = spy(new EIP712Domain(wrongDomainName, "1", chainId, null));
        final EIP712Challenge challenge = spy(new EIP712Challenge(domain, null));

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(chainId);
        when(resultProxyClient.getChallenge(chainId)).thenReturn(challenge);

        assertThat(resultService.getIexecUploadToken()).isEmpty();

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(chainId);
        verify(credentialsService, never()).signEIP712EntityAndBuildToken(any());
        verify(resultProxyClient, never()).login(anyInt(), any());

        verify(challenge, times(1)).getDomain();
        verify(domain, times(1)).getName();
        verify(domain, times(0)).getChainId();
    }

    @Test
    void shouldNotGetIexecUploadTokenSinceWrongChainId() {
        final int expectedChainId = 1;
        final long wrongChainId = 42;
        final EIP712Domain domain = spy(new EIP712Domain("iExec Result Repository", "1", wrongChainId, null));
        final EIP712Challenge challenge = spy(new EIP712Challenge(domain, null));

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(expectedChainId);
        when(resultProxyClient.getChallenge(expectedChainId)).thenReturn(challenge);

        assertThat(resultService.getIexecUploadToken()).isEmpty();

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(expectedChainId);
        verify(credentialsService, never()).signEIP712EntityAndBuildToken(any());
        verify(resultProxyClient, never()).login(anyInt(), any());

        verify(challenge, times(1)).getDomain();
        verify(domain, times(1)).getName();
        verify(domain, times(1)).getChainId();
    }

    @Test
    void shouldNotGetIexecUploadTokenSinceSigningReturnsEmpty() {
        final int chainId = 1;
        final EIP712Domain domain = spy(new EIP712Domain("iExec Result Repository", "1", chainId, null));
        final EIP712Challenge challenge = spy(new EIP712Challenge(domain, null));

        when(blockchainAdapterConfigurationService.getChainId()).thenReturn(chainId);
        when(resultProxyClient.getChallenge(chainId)).thenReturn(challenge);
        when(credentialsService.signEIP712EntityAndBuildToken(challenge)).thenReturn("");

        assertThat(resultService.getIexecUploadToken()).isEmpty();

        verify(blockchainAdapterConfigurationService, times(1)).getChainId();
        verify(resultProxyClient, times(1)).getChallenge(chainId);
        verify(credentialsService, times(1)).signEIP712EntityAndBuildToken(challenge);
        verify(resultProxyClient, never()).login(anyInt(), any());

        verify(challenge, times(1)).getDomain();
        verify(domain, times(1)).getName();
        verify(domain, times(1)).getChainId();
    }
    //endregion

    // region purgeTask
    @Test
    void shouldPurgeTask() {
        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        assertThat(resultService.purgeTask(CHAIN_TASK_ID))
                .isTrue();
    }

    @Test
    void shouldNotPurgeTaskSinceDirDeletionFailed() {
        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        try (MockedStatic<FileHelper> fileHelper = Mockito.mockStatic(FileHelper.class)) {
            fileHelper.when(() -> FileHelper.deleteFolder(tmp))
                    .thenReturn(false);

            assertThat(resultService.purgeTask(CHAIN_TASK_ID))
                    .isFalse();
        }
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksData() {
        final Map<String, ResultInfo> resultInfoMap = new HashMap<>(Map.of(
                CHAIN_TASK_ID, ResultInfo.builder().build(),
                CHAIN_TASK_ID_2, ResultInfo.builder().build()
        ));
        ReflectionTestUtils.setField(resultService, "resultInfoMap", resultInfoMap);

        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(workerConfigurationService.getTaskBaseDir(CHAIN_TASK_ID_2))
                .thenReturn(tmp);

        resultService.purgeAllTasksData();

        assertThat(resultInfoMap).isEmpty();
        assertThat(new File(tmp)).doesNotExist();
    }
    // endregion
}
