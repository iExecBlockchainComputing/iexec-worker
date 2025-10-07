/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.result.ComputedFile;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecFileHelper;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.resultproxy.api.ResultProxyClient;
import com.iexec.worker.chain.IexecHubService;
import com.iexec.worker.config.PublicConfigurationService;
import com.iexec.worker.config.WorkerConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.iexec.commons.poco.chain.DealParams.DROPBOX_RESULT_STORAGE_PROVIDER;
import static com.iexec.commons.poco.chain.DealParams.IPFS_RESULT_STORAGE_PROVIDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultServiceTests {

    public static final String RESULT_DIGEST = "0x0000000000000000000000000000000000000000000000000000000000000001";
    // 32 + 32 + 1 = 65 bytes
    public static final String ENCLAVE_SIGNATURE = "0x000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000b0c";
    private static final String CHAIN_DEAL_ID = "0xe1f3b96f58be8d5d1958ac14b6a3e93497ad9985ea44ac8c79f613129fff79a0";
    private static final String CHAIN_TASK_ID = "0x7602291763f60943833c39a11b7e81f1f372f29b102bffad5b23c62bde0ef70e";
    private static final String CHAIN_TASK_ID_2 = "taskId2";
    private static final String IEXEC_WORKER_TMP_FOLDER = "./src/test/resources/tmp/test-worker/";
    private static final String CALLBACK = "0x0000000000000000000000000000000000000abc";
    private static final String CUSTOM_RESULT_PROXY_URL = "https://custom-proxy.iex.ec";

    private static final String AUTHORIZATION = "0x4";
    private static final ChainTask CHAIN_TASK = ChainTask.builder()
            .dealid(CHAIN_DEAL_ID)
            .chainTaskId(CHAIN_TASK_ID)
            .status(ChainTaskStatus.ACTIVE)
            .build();
    private static final ChainDeal CHAIN_DEAL = ChainDeal.builder()
            .chainDealId(CHAIN_DEAL_ID)
            .tag(TeeUtils.TEE_SCONE_ONLY_TAG)
            .params(DealParams.builder()
                    .iexecResultStorageProxy(CUSTOM_RESULT_PROXY_URL)
                    .build())
            .build();
    private static final WorkerpoolAuthorization WORKERPOOL_AUTHORIZATION = WorkerpoolAuthorization.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .enclaveChallenge("0x2")
            .workerWallet("0x3")
            .build();

    @TempDir
    public File folderRule;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ResultProxyClient resultProxyClient;
    @Mock
    private PublicConfigurationService publicConfigurationService;
    @Mock
    private WorkerConfigurationService workerConfigurationService;
    @Mock
    private SignerService signerService;

    @InjectMocks
    private ResultService resultService;

    private String tmp;

    @BeforeEach
    void init() {
        tmp = folderRule.getAbsolutePath();
    }

    // region writeErrorToIexecOut
    @Test
    void shouldWriteErrorToIexecOut() {
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                List.of(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED));

        assertThat(isErrorWritten).isTrue();
        String errorFileAsString = FileHelper.readFile(tmp + "/"
                + ResultService.ERROR_FILENAME);
        assertThat(errorFileAsString)
                .contains("[IEXEC] Error occurred while computing the task")
                .contains("INPUT_FILES_DOWNLOAD_FAILED");
        String computedFileAsString = FileHelper.readFile(tmp + "/"
                + IexecFileHelper.COMPUTED_JSON);
        assertThat(computedFileAsString).isEqualTo("{" +
                "\"deterministic-output-path\":\"/iexec_out/error.txt\"," +
                "\"callback-data\":null," +
                "\"task-id\":null," +
                "\"result-digest\":null," +
                "\"enclave-signature\":null," +
                "\"error-message\":null" +
                "}");
    }

    @Test
    void shouldNotWriteErrorToIexecOutSince() {
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn("/null");

        boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                List.of(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED));

        assertThat(isErrorWritten).isFalse();
    }

    @Test
    void shouldWriteMultipleErrorsToIexecOut() {
        when(workerConfigurationService.getTaskIexecOutDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        final boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                List.of(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED,
                        ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED));

        assertThat(isErrorWritten).isTrue();
        final String errorFileAsString = FileHelper.readFile(tmp + "/"
                + ResultService.ERROR_FILENAME);
        assertThat(errorFileAsString)
                .contains("[IEXEC] Error occurred while computing the task")
                .contains("INPUT_FILES_DOWNLOAD_FAILED")
                .contains("DATASET_FILE_DOWNLOAD_FAILED");
    }

    @Test
    void shouldNotWriteErrorToIexecOutSinceEmptyCausesList() {
        final boolean isErrorWritten = resultService.writeErrorToIexecOut(CHAIN_TASK_ID,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                List.of());

        assertThat(isErrorWritten).isFalse();
    }
    // endregion

    // region uploadResultAndGetLink
    @Test
    void shouldNotGetResultLinkWhenNoTask() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(null);
        assertThat(resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION)).isEmpty();
    }

    @Test
    void shouldGetWeb3ResultLink() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().callback(CALLBACK).build());
        final String resultLink = resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION);
        assertThat(resultLink).isEqualTo(resultService.buildResultLink("ethereum", CALLBACK));
    }

    @Test
    void shouldGetTeeWeb2ResultLinkSinceIpfs() {
        final String storage = IPFS_RESULT_STORAGE_PROVIDER;
        final String ipfsHash = "QmcipfsHash";
        final DealParams dealParams = DealParams.builder()
                .iexecResultStorageProvider(storage)
                .iexecResultStorageProxy(CUSTOM_RESULT_PROXY_URL)
                .build();
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder()
                        .chainTaskId(CHAIN_TASK_ID)
                        .isTeeTask(true)
                        .dealParams(dealParams)
                        .build());
        when(resultProxyClient.getIpfsHashForTask(CHAIN_TASK_ID)).thenReturn(ipfsHash);
        when(publicConfigurationService.createResultProxyClientFromURL(CUSTOM_RESULT_PROXY_URL))
                .thenReturn(resultProxyClient);

        final String resultLink = resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION);

        assertThat(resultLink).isEqualTo(resultService.buildResultLink(storage, "/ipfs/" + ipfsHash));
        verify(publicConfigurationService).createResultProxyClientFromURL(CUSTOM_RESULT_PROXY_URL);
        verify(resultProxyClient).getIpfsHashForTask(CHAIN_TASK_ID);
    }

    @Test
    void shouldGetTeeWeb2ResultLinkSinceDropbox() {
        final String storage = DROPBOX_RESULT_STORAGE_PROVIDER;
        final DealParams dealParams = DealParams.builder()
                .iexecResultStorageProvider(storage)
                .build();

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).isTeeTask(true).dealParams(dealParams).build());

        final String resultLink = resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION);

        assertThat(resultLink).isEqualTo(resultService.buildResultLink(storage, "/results/" + CHAIN_TASK_ID));
    }

    @Test
    void shouldNotGetTeeWeb2ResultLinkSinceBadStorage() {
        final String storage = "some-unsupported-third-party-storage";
        final DealParams dealParams = DealParams.builder()
                .iexecResultStorageProvider(storage)
                .build();

        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).isTeeTask(true).dealParams(dealParams).build());

        final String resultLink = resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION);

        assertThat(resultLink).isEmpty();
    }

    @Test
    void shouldNotGetWeb2ResultLinkForStandardTaskOnIpfs() {
        final String uploadToken = "uploadToken";
        final DealParams dealParams = DealParams.builder()
                .iexecResultStorageProvider(IPFS_RESULT_STORAGE_PROVIDER)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(CHAIN_TASK));
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().chainTaskId(CHAIN_TASK_ID).dealParams(dealParams).build());
        when(signerService.signMessageHash(anyString())).thenReturn(new Signature(AUTHORIZATION));

        ResultProxyClient mockClient = mock(ResultProxyClient.class);
        when(mockClient.getJwt(AUTHORIZATION, WORKERPOOL_AUTHORIZATION)).thenReturn(uploadToken);
        when(publicConfigurationService.createResultProxyClientFromURL(CUSTOM_RESULT_PROXY_URL))
                .thenReturn(mockClient);

        final String resultLink = resultService.uploadResultAndGetLink(WORKERPOOL_AUTHORIZATION);

        assertThat(resultLink).isEmpty();
    }
    // endregion

    //region getComputedFile
    @Test
    void shouldGetComputedFileWithWeb2ResultDigestSinceFile() {
        String chainTaskId = "deterministic-output-file";

        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo(
                "0x09b727883db89fa3b3504f83e0c67d04a0d4fc35a9670cc4517c49d2a27ad171");
    }

    @Test
    void shouldGetComputedFileWithWeb2ResultDigestSinceFileTree() {
        String chainTaskId = "deterministic-output-directory";

        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId +
                        "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());

        ComputedFile computedFile =
                resultService.getComputedFile(chainTaskId);
        String hash = computedFile.getResultDigest();
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo(
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
        // should be equal to the content of the file since it is a byte32
        assertThat(hash).isEqualTo(
                "0xb10e2d527612073b26eecdfd717e6a320cf44b4afac2b0732d9fcbe2b7fa0cf6");
    }

    @Test
    void shouldNotGetComputedFileWhenFileNotFound() {
        String chainTaskId = "does-not-exist";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        assertThat(computedFile).isNull();
    }

    @Test
    void shouldNotComputeWeb2ResultDigestWhenFileTreeNotFound() {
        String chainTaskId = "deterministic-output-directory-missing";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(BytesUtils.EMPTY_ADDRESS).build());
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        assertThat(computedFile).isNull();
        computedFile = resultService.readComputedFile(chainTaskId);
        assertThat(computedFile).isNotNull();
        String resultDigest = resultService.computeResultDigest(computedFile);
        assertThat(resultDigest).isEmpty();
    }

    @Test
    void shouldNotComputeWeb3ResultDigestWhenNoCallbackData() {
        String chainTaskId = "callback-no-data";
        when(workerConfigurationService.getTaskOutputDir(chainTaskId))
                .thenReturn(IEXEC_WORKER_TMP_FOLDER + chainTaskId + "/output");
        when(iexecHubService.getTaskDescription(chainTaskId)).thenReturn(
                TaskDescription.builder().callback(CALLBACK).build());
        ComputedFile computedFile = resultService.getComputedFile(chainTaskId);
        assertThat(computedFile).isNull();
        computedFile = resultService.readComputedFile(chainTaskId);
        assertThat(computedFile).isNotNull();
        String resultDigest = resultService.computeResultDigest(computedFile);
        assertThat(resultDigest).isEmpty();
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
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isTrue();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        ComputedFile writtenComputeFile = new ObjectMapper()
                .readValue(writtenComputeFileAsString, ComputedFile.class);
        assertThat(writtenComputeFile).isEqualTo(computedFile);
    }

    @Test
    void shouldNotWriteComputedFileSinceNothingToWrite() {
        boolean isWritten = resultService.writeComputedFile(null);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
        verifyNoInteractions(iexecHubService, workerConfigurationService);
    }

    @Test
    void shouldNotWriteComputedFileSinceNoChainTaskId() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId("")
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
        verifyNoInteractions(iexecHubService, workerConfigurationService);
    }

    @Test
    void shouldNotWriteComputedFileSinceNotActive() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(ChainTask.builder().status(ChainTaskStatus.UNSET).build()));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
        verifyNoInteractions(workerConfigurationService);
    }

    @Test
    void shouldNotWriteComputedFileSinceAlreadyWritten() throws JsonProcessingException {
        ComputedFile newComputedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("0x0000000000000000000000000000000000000000000000000000000000000003")
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));

        //mock old file already written
        resultService.writeComputedFile(ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build());
        //write new file
        boolean isWritten = resultService.writeComputedFile(newComputedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        ComputedFile writtenComputeFile = new ObjectMapper()
                .readValue(writtenComputeFileAsString, ComputedFile.class);
        assertThat(writtenComputeFile).isNotEqualTo(newComputedFile);
    }

    @Test
    void shouldNotWriteComputedFileSinceResultDigestIsEmpty() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("")
                .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceResultDigestIsInvalid() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest("0x01")
                .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceNotTeeTask() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(BytesUtils.EMPTY_HEX_STRING_32)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(ChainDeal.builder()
                .chainDealId(CHAIN_DEAL_ID)
                .tag(BytesUtils.EMPTY_HEX_STRING_32)
                .build()));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceSignatureIsRequiredAndEmpty() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature("")
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceSignatureIsRequiredAndInvalid() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature("0x01")
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(tmp);
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
        String writtenComputeFileAsString = FileHelper.readFile(tmp +
                IexecFileHelper.SLASH_COMPUTED_JSON);
        assertThat(writtenComputeFileAsString).isEmpty();
    }

    @Test
    void shouldNotWriteComputedFileSinceWriteFailed() {
        ComputedFile computedFile = ComputedFile.builder()
                .taskId(CHAIN_TASK_ID)
                .resultDigest(RESULT_DIGEST)
                .enclaveSignature(ENCLAVE_SIGNATURE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID))
                .thenReturn(Optional.of(CHAIN_TASK));
        when(workerConfigurationService.getTaskOutputDir(CHAIN_TASK_ID))
                .thenReturn(":somewhere");
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));

        boolean isWritten = resultService.writeComputedFile(computedFile);

        assertThat(isWritten).isFalse();
    }
    //endregion

    //region getIexecUploadToken
    @Test
    void shouldGetIexecUploadTokenFromWorkerpoolAuthorization() {
        final String uploadToken = "uploadToken";
        when(signerService.signMessageHash(anyString())).thenReturn(new Signature(AUTHORIZATION));
        when(resultProxyClient.getJwt(AUTHORIZATION, WORKERPOOL_AUTHORIZATION)).thenReturn(uploadToken);
        when(publicConfigurationService.createResultProxyClientFromURL(CUSTOM_RESULT_PROXY_URL)).thenReturn(resultProxyClient);

        String result = resultService.getIexecUploadToken(WORKERPOOL_AUTHORIZATION, CUSTOM_RESULT_PROXY_URL);

        assertThat(result).isEqualTo(uploadToken);
        verify(signerService).signMessageHash(anyString());
        verify(publicConfigurationService).createResultProxyClientFromURL(CUSTOM_RESULT_PROXY_URL);
        verify(resultProxyClient).getJwt(AUTHORIZATION, WORKERPOOL_AUTHORIZATION);
    }

    @Test
    void shouldNotGetIexecUploadTokenWorkerpoolAuthorizationSinceSigningReturnsEmpty() {
        when(signerService.signMessageHash(anyString())).thenReturn(new Signature(""));
        assertThat(resultService.getIexecUploadToken(WORKERPOOL_AUTHORIZATION, CUSTOM_RESULT_PROXY_URL)).isEmpty();
        verify(signerService).signMessageHash(anyString());
        verifyNoInteractions(publicConfigurationService);
    }

    @Test
    void shouldNotGetIexecUploadTokenFromWorkerpoolAuthorizationSinceFeignException() {
        when(signerService.signMessageHash(anyString())).thenReturn(new Signature(AUTHORIZATION));

        assertThat(resultService.getIexecUploadToken(WORKERPOOL_AUTHORIZATION, CUSTOM_RESULT_PROXY_URL)).isEmpty();
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

        try (MockedStatic<FileHelper> fileHelper = mockStatic(FileHelper.class)) {
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
